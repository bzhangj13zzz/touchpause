/*
blockevent.c - blockevent for Android v0.4.6

Copyright 2022 N. Melih Sensoy

Modified 2026-08-12 for TouchQuell: multi-node release-key monitoring,
single-instance locking, readiness signaling, and fail-safe input polling.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <ctype.h>
#include <fcntl.h>
#include <linux/input.h>
#include <signal.h>
#include <stdint.h>
#include <dirent.h>
#include <string.h>
#include <sys/poll.h>
#include <stdbool.h>
#include <linux/uinput.h>
#include <errno.h>
#include <limits.h>

#define VERSION "0.4.6-touchquell.1"
#define DEV_DIR "/dev/input"
#define VIRTUAL_DEV_LOC "/dev/uinput"
#define VIRTUAL_DEV_NAME "touchquell-input-"VERSION"_virtual_dev"
#define PRESET_DEV_CNT 4
#define POLL_TIMEOUT_MS 250
#define READY_MESSAGE "BLOCKER_READY"

typedef struct {
    int x;
    int y;
} Point;

typedef struct {
    Point left_bottom;
    Point right_top;
} Rect;

static int nfds;
static struct pollfd *pfds;
static char **devs;
static uint8_t *roles;
static int dev_nid[] = {-1, -1, -1, -1, -1};
static int uinp_fd = -1;
static struct uinput_user_dev uinp;
static int instance_lock_fd = -1;
static const char *instance_lock_path;

volatile sig_atomic_t e_flag = 0;
static void sig_handler(int signum);

typedef enum {
    DEV_TOUCHSCREEN      = 1U << 0,
    DEV_VOLUP            = 1U << 1,
    DEV_VOLDOWN          = 1U << 2,
    DEV_ANY              = 1U << 3,
    DEV_POWERBTN         = 1U << 4,
    ID_TOUCHSCREEN       = 1,
    ID_VOLUP             = 2,
    ID_VOLDOWN           = 3,
    ID_POWER             = 4,
} dev_types;

enum {
    ROLE_BLOCK            = 1U << 0,
    ROLE_TRIGGER          = 1U << 1,
    ST_SCAN              = 1U << 0,
    ST_TRIGGER           = 1U << 1,
    ST_CUSTOM_TRIGGER    = 1U << 2,
    ST_TS_BLOCK_PARTLY   = 1U << 3,
    ST_REVERSE_BLOCK     = 1U << 4,
    ST_VIRT_BUTTON       = 1U << 5,
    PRINT_ERR            = 1U << 0,
    PRINT_NONE           = 1U << 1,
    PRINT_ALL            = 1U << 2,
    PRINT_ISSET          = 1U << 3,
};

/**
 * Returns whether name is an Android event device entry (event followed by digits).
 * Input: a single directory-entry name. Output: true only for event[0-9]+.
 */
static bool is_event_device_name(const char *name)
{
    size_t i;

    if (strncmp(name, "event", 5) != 0 || name[5] == '\0')
        return false;

    for (i = 5; name[i] != '\0'; i++) {
        if (!isdigit((unsigned char)name[i]))
            return false;
    }
    return true;
}

/**
 * Finds an already-open device path.
 * Input: absolute device path. Output: its descriptor index, or -1 when absent.
 */
static int find_device(const char *device)
{
    int i;

    for (i = 0; i < nfds; i++) {
        if (strcmp(device, devs[i]) == 0)
            return i;
    }
    return -1;
}

/**
 * Adds an open input descriptor to the poll set.
 * Inputs: owned fd, absolute path, and ROLE_* bits. Output: new index or -1.
 */
static int append_device(int fd, const char *device, uint8_t role)
{
    struct pollfd *new_pfds;
    char **new_devs;
    uint8_t *new_roles;

    new_pfds = realloc(pfds, sizeof(*pfds) * (nfds + 1));
    if (new_pfds == NULL)
        return -1;
    pfds = new_pfds;

    new_devs = realloc(devs, sizeof(*devs) * (nfds + 1));
    if (new_devs == NULL)
        return -1;
    devs = new_devs;

    new_roles = realloc(roles, sizeof(*roles) * (nfds + 1));
    if (new_roles == NULL)
        return -1;
    roles = new_roles;

    devs[nfds] = strdup(device);
    if (devs[nfds] == NULL)
        return -1;
    pfds[nfds].fd = fd;
    pfds[nfds].events = POLLIN;
    pfds[nfds].revents = 0;
    roles[nfds] = role;
    return nfds++;
}

static const struct dev_preset {
	dev_types dev_type;
    dev_types dev_id;
    union{
       struct input_event event;
       Rect *area;
    };

} dev_presets[] = {
    { DEV_TOUCHSCREEN, ID_TOUCHSCREEN, .area=NULL},
	{ DEV_VOLDOWN, ID_VOLDOWN,
        .event = {.type = EV_KEY, .code = KEY_VOLUMEDOWN, .value = 1}},
    { DEV_VOLUP, ID_VOLUP,
        .event = {.type = EV_KEY, .code = KEY_VOLUMEUP, .value = 1}},
    { DEV_POWERBTN, ID_POWER,
        .event = {.type = EV_KEY, .code = KEY_POWER, .value = 1}},
};

static inline bool test_bit(unsigned bit, unsigned char *array)
{
  return array[bit / 8] & (1 << (bit % 8));
}

static inline bool has_touchscreen_capabilities(
    unsigned char *abs_mask,
    unsigned char *key_mask,
    unsigned char *prop_mask)
{
    bool has_position =
        (test_bit(ABS_MT_POSITION_X, abs_mask) && test_bit(ABS_MT_POSITION_Y, abs_mask)) ||
        (test_bit(ABS_X, abs_mask) && test_bit(ABS_Y, abs_mask));

    return has_position && test_bit(BTN_TOUCH, key_mask) &&
        test_bit(INPUT_PROP_DIRECT, prop_mask);
}

static inline void print_err(const char *format, const char *text,
    int number, uint8_t flags)
{
    if (flags & (PRINT_ERR | PRINT_ALL))
        text != NULL ? fprintf(stderr, format, text) : fprintf(stderr, format, number);
}

static inline void print_all(const char *format, const char *text,
    int number, uint8_t flags)
{
    if (flags & PRINT_ALL)
        text != NULL ? fprintf(stderr, format, text) : fprintf(stderr, format, number);
}

static inline void print_event(struct input_event *ev, int dev_id)
{
    fprintf(stderr, "[INFO] dev: %s Event: Type[%d] Code[%d] Value[%d]\n",
        devs[dev_id], ev->type, ev->code, ev->value);
}

static inline int pixel_to_raw(int pixel, int min, int max, int axis_length)
{
    return (pixel * (max - min + 1) / axis_length ) + min;
}

static inline bool are_same_event(struct input_event *ev1, struct input_event *ev2)
{
    return ((ev1->code == ev2->code) && (ev1->type == ev2->type) && (ev1->value == ev2->value));
}

static inline bool is_in_area(Point *p1, Rect *r1)
{
   return (p1->x >= r1->left_bottom.x
        && p1->y <= r1->left_bottom.y
        && p1->x <= r1->right_top.x
        && p1->y >= r1->right_top.y);
}

static inline void set_point_from_event(Point *p, struct input_event *ev)
{
    if (ev->code == ABS_MT_POSITION_X)
        p->x = ev->value;
    if (ev->code == ABS_MT_POSITION_Y)
        p->y = ev->value;
}

static inline bool detect_double_tap(int64_t *now, int64_t *prev, struct input_event *ev, u_int32_t timeout)
{
    *now = ev->time.tv_sec * 1000000LL + ev->time.tv_usec; // current time in microseconds.
    if (*now - *prev < timeout)
        return true;
    *prev = *now;
    return false;
}

static int parse_pixel_rect(char *raw_text, int **return_coords, char *del, int width, int height, uint8_t print_flags)
{
    int i = 0;
    char *tmp = NULL;
    raw_text = strtok(raw_text, del); // x1,y1,x2,y2
    while (raw_text != NULL && i < 4) {
        *return_coords[i] = (int)strtol(raw_text, &tmp, 0);
        if (tmp == raw_text){
            print_err("[ERR] '%s' not a decimal number\n", raw_text, 0, print_flags);
            return -1;
        }

        if ((i & 1) != 0) // = !isEven
            *return_coords[i] = pixel_to_raw(*return_coords[i], 0, width, width);
        else
            *return_coords[i] = pixel_to_raw(*return_coords[i], 0, height, height);

        raw_text = strtok(NULL, del);
        i++;
    }
    if (i != 4)
        return -1;

    return 0;
}

static int clone_device(int dev_id, uint8_t dev_type, uint8_t print_flags)
{
    int i;
    uint8_t abs_mask[ABS_CNT / 8];
    struct input_absinfo abs_limit;
    char str[100];

    if (dev_id < 0 || dev_id >= nfds || pfds[dev_id].fd < 0){
        print_err("[ERR] no device found to clone.\n", NULL, 0, print_flags);
        return -1;
    }

    if (dev_type == 0){
        print_err("[ERR] type for clone device is not specified.\n", NULL, 0, print_flags);
        return -1;
    }

    uinp_fd = open(VIRTUAL_DEV_LOC, O_WRONLY|O_NONBLOCK);
    if(uinp_fd < 0) {
        print_err("[ERR] Unable to open /dev/uinput\n", NULL, 0, print_flags);
        return -1;
    }

    memset(&uinp, 0, sizeof(uinp));
    strncpy(uinp.name, VIRTUAL_DEV_NAME, UINPUT_MAX_NAME_SIZE);
    uinp.id.version = 4;
    uinp.id.bustype = BUS_VIRTUAL;

    if (dev_type & DEV_TOUCHSCREEN){
        ioctl(uinp_fd, UI_SET_EVBIT, EV_KEY);
        ioctl(uinp_fd, UI_SET_EVBIT, EV_ABS);
        ioctl(uinp_fd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);
        ioctl(uinp_fd, UI_SET_KEYBIT, BTN_TOUCH);

        ioctl(pfds[dev_id].fd, EVIOCGBIT(EV_ABS, sizeof(abs_mask)), abs_mask);

        for (i = 0; i < ABS_MAX; i++) {
            if (test_bit(i, abs_mask)) {
                ioctl(pfds[dev_id].fd, EVIOCGABS(i), &abs_limit);

                uinp.absmin[i] = abs_limit.minimum;
                uinp.absmax[i] = abs_limit.maximum;
                ioctl(uinp_fd, UI_SET_ABSBIT, i);
            }
        }
    }

    memset(str, 0, sizeof(str));
    sprintf(str, "%d", uinp_fd);

    write(uinp_fd, &uinp, sizeof(uinp));
    if (ioctl(uinp_fd, UI_DEV_CREATE) < 0){
        print_err("[ERR] An error occured when creating the '%s'.\n", VIRTUAL_DEV_NAME, 0, print_flags);
        return -1;
    }

    print_all("[INFO] '%s' created successfully. \n", VIRTUAL_DEV_NAME, 0, print_flags);
    usleep(10000);

    return 0;
}

static int open_device(const char *device, uint8_t *classes, uint8_t print_flags,
    bool return_dev, uint8_t role)
{
    int fd;
    int existing;
    int added;
    bool owns_fd = false;
    uint8_t abs_mask[(ABS_CNT + 7) / 8] = {0};
    uint8_t key_mask[(KEY_CNT + 7) / 8] = {0};
    uint8_t prop_mask[(INPUT_PROP_CNT + 7) / 8] = {0};
    uint8_t is_recognized = 0;
    uint8_t dev_type = 0;
    char name[80] = "?";

    existing = find_device(device);
    if (existing >= 0) {
        if (*classes & DEV_ANY) {
            roles[existing] |= role;
            *classes &= ~DEV_ANY;
            return return_dev ? existing : 0;
        }
        fd = pfds[existing].fd;
    } else {
        print_all("[INFO] '%s' is opening...\n", device, 0, print_flags);
        fd = open(device, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if(fd < 0) {
            print_err("[ERR] '%s' could not open. \n", device, 0, print_flags);
            return -1;
        }
        owns_fd = true;
    }

    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs_mask)), abs_mask) < 0 ||
        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_mask)), key_mask) < 0 ||
        ioctl(fd, EVIOCGPROP(sizeof(prop_mask)), prop_mask) < 0) {
        print_all("[INFO] '%s' has no readable input capabilities.\n", device, 0, print_flags);
        if (owns_fd)
            close(fd);
        return -1;
    }

    if ((is_recognized == 0) && (*classes & DEV_ANY)){
        print_all("[INFO] 'DEV_ANY' detected.\n", NULL, 0, print_flags);
        is_recognized |= 1;
        if (has_touchscreen_capabilities(abs_mask, key_mask, prop_mask))
            dev_type = ID_TOUCHSCREEN;
        *classes &= ~DEV_ANY;
    }

    // power button detection shortcut for qualcomm devices
    if ((is_recognized == 0) && (*classes & DEV_POWERBTN)){
        ioctl(fd, EVIOCGNAME(sizeof(name)), name);
        if (strcmp(name, "qpnp_pon") == 0){
            print_all("[INFO] Qualcomm device'DEV_POWERBTN' detected...\n", NULL, 0, print_flags);
            is_recognized |= 1;
            dev_type = ID_POWER;
            *classes &= ~DEV_POWERBTN;
        }
    }

    /*
    I followed here to get capabilities of devices.
    https://www.linuxjournal.com/article/6429

    I followed here to classify devices with using their capabilities.
    https://source.android.com/devices/input/touch-devices
    */
    if ((is_recognized == 0) && (*classes & DEV_TOUCHSCREEN)){
        if (has_touchscreen_capabilities(abs_mask, key_mask, prop_mask)){
            print_all("[INFO] 'DEV_TOUCHSCREEN' detected...\n", NULL, 0, print_flags);
            is_recognized |= 1;
            dev_type = ID_TOUCHSCREEN;
            *classes &= ~DEV_TOUCHSCREEN;
        }
    }

    if ((is_recognized == 0) && (*classes & DEV_VOLDOWN) && test_bit(KEY_VOLUMEDOWN, key_mask)){
        if (!test_bit(KEY_MEDIA, key_mask)){
            print_all("[INFO] 'DEV_VOLDOWN' detected...\n", NULL, 0, print_flags);
            is_recognized |= 1;
            dev_type = ID_VOLDOWN;
            *classes &= ~DEV_VOLDOWN;
        }
    }
    if ((is_recognized == 0) && (*classes & DEV_VOLUP) && test_bit(KEY_VOLUMEUP, key_mask)){
        if (!test_bit(KEY_MEDIA, key_mask)){
            print_all("[INFO] 'DEV_VOLUP' detected...\n", NULL, 0, print_flags);
            is_recognized |= 1;
            dev_type = ID_VOLUP;
            *classes &= ~DEV_VOLUP;
        }
    }
    if ((is_recognized == 0) && (*classes & DEV_POWERBTN) && test_bit(KEY_POWER, key_mask)){
        print_all("[INFO] 'DEV_POWERBTN' detected...\n", NULL, 0, print_flags);
        is_recognized |= 1;
        dev_type = ID_POWER;
        *classes &= ~DEV_POWERBTN;
    }

    if (is_recognized & 1){
        if (existing >= 0) {
            roles[existing] |= role;
            if (dev_type > 0)
                dev_nid[dev_type] = existing;
            return return_dev ? existing : 0;
        }

        added = append_device(fd, device, role);
        if (added < 0) {
            close(fd);
            return -1;
        }
        if (dev_type > 0)
            dev_nid[dev_type] = added;
        return return_dev ? added : 0;
    }

    print_all("[INFO] Unknown device.Closing...\n\n", NULL, 0, print_flags);
    if (owns_fd)
        close(fd);
    return -1;
}

/**
 * Acquires the single-instance lock or signals its current owner to stop.
 * Input: lock-file path. Output: 0=owned, 1=existing owner signaled, -1=error.
 */
static int acquire_or_toggle_instance(const char *path, uint8_t print_flags)
{
    struct flock lock = {
        .l_type = F_WRLCK,
        .l_whence = SEEK_SET,
        .l_start = 0,
        .l_len = 0,
    };
    char pid_text[32];
    ssize_t length;

    if (path == NULL)
        return 0;

    instance_lock_fd = open(path, O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    if (instance_lock_fd < 0) {
        print_err("[ERR] could not open instance lock '%s'.\n", path, 0, print_flags);
        return -1;
    }

    if (fcntl(instance_lock_fd, F_SETLK, &lock) == 0) {
        length = snprintf(pid_text, sizeof(pid_text), "%ld\n", (long)getpid());
        if (length <= 0 || length >= (ssize_t)sizeof(pid_text) ||
            ftruncate(instance_lock_fd, 0) < 0 ||
            lseek(instance_lock_fd, 0, SEEK_SET) < 0 ||
            write(instance_lock_fd, pid_text, (size_t)length) != length) {
            print_err("[ERR] could not initialize the instance lock.\n", NULL, 0, print_flags);
            close(instance_lock_fd);
            instance_lock_fd = -1;
            return -1;
        }
        instance_lock_path = path;
        return 0;
    }

    if (errno != EACCES && errno != EAGAIN) {
        print_err("[ERR] could not acquire the instance lock.\n", NULL, 0, print_flags);
        close(instance_lock_fd);
        instance_lock_fd = -1;
        return -1;
    }

    /* F_GETLK reports the actual kernel lock owner, so stale file contents are harmless. */
    lock.l_type = F_WRLCK;
    if (fcntl(instance_lock_fd, F_GETLK, &lock) < 0) {
        print_err("[ERR] could not identify the running instance.\n", NULL, 0, print_flags);
        close(instance_lock_fd);
        instance_lock_fd = -1;
        return -1;
    }

    /* The owner may have finished between F_SETLK and F_GETLK; that already satisfies stop. */
    if (lock.l_type == F_UNLCK) {
        close(instance_lock_fd);
        instance_lock_fd = -1;
        return 1;
    }

    if (lock.l_pid <= 1) {
        print_err("[ERR] invalid instance-lock owner.\n", NULL, 0, print_flags);
        close(instance_lock_fd);
        instance_lock_fd = -1;
        return -1;
    }

    if (kill(lock.l_pid, SIGINT) < 0 && errno != ESRCH) {
        print_err("[ERR] could not stop the running instance.\n", NULL, 0, print_flags);
        close(instance_lock_fd);
        instance_lock_fd = -1;
        return -1;
    }

    close(instance_lock_fd);
    instance_lock_fd = -1;
    return 1;
}

static void release_instance_lock(void)
{
    if (instance_lock_fd < 0)
        return;

    if (instance_lock_path != NULL)
        unlink(instance_lock_path);
    close(instance_lock_fd);
    instance_lock_fd = -1;
    instance_lock_path = NULL;
}

static void close_devices(uint8_t print_flags)
{
    nfds--;
    while(nfds >= 0) {
        print_all("[INFO] '%s' closing...\n", devs[nfds], 0, print_flags);
        if (pfds[nfds].fd >= 0) {
            if (roles[nfds] & ROLE_BLOCK)
                ioctl(pfds[nfds].fd, EVIOCGRAB, 0);
            close(pfds[nfds].fd);
        }
        free(devs[nfds]);
        nfds--;
    }
    if (uinp_fd >= 0){
        usleep(10000);
        ioctl(uinp_fd, UI_DEV_DESTROY);
        close(uinp_fd);
    }
    free(pfds);
    free(devs);
    free(roles);
    release_instance_lock();
}

static int scan_devices(uint8_t classes, uint8_t print_flags)
{
    char devloc[PATH_MAX];
    DIR *dir;
    struct dirent *dp;
    if ((dir = opendir(DEV_DIR)) == NULL) return -1;

    while ((dp = readdir(dir)) != NULL) {
        if (!is_event_device_name(dp->d_name))
            continue;
        if (snprintf(devloc, sizeof(devloc), "%s/%s", DEV_DIR, dp->d_name) >=
            (int)sizeof(devloc))
            continue;

        open_device(devloc, &classes, print_flags, false, ROLE_BLOCK);
        if (classes == 0)
            break;
    }
    closedir(dir);
    return classes;
}

/**
 * Opens every input node that advertises trigger_code.
 * Input: Linux EV_KEY code. Output: candidate count, or -1 on scan failure.
 */
static int scan_trigger_candidates(unsigned trigger_code, uint8_t print_flags)
{
    char devloc[PATH_MAX];
    DIR *dir;
    struct dirent *dp;
    int candidates = 0;

    dir = opendir(DEV_DIR);
    if (dir == NULL)
        return -1;

    while ((dp = readdir(dir)) != NULL) {
        uint8_t key_mask[(KEY_CNT + 7) / 8] = {0};
        int existing;
        int fd;

        if (!is_event_device_name(dp->d_name))
            continue;
        if (snprintf(devloc, sizeof(devloc), "%s/%s", DEV_DIR, dp->d_name) >=
            (int)sizeof(devloc))
            continue;

        existing = find_device(devloc);
        fd = existing >= 0
            ? pfds[existing].fd
            : open(devloc, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0)
            continue;

        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_mask)), key_mask) >= 0 &&
            test_bit(trigger_code, key_mask)) {
            if (existing >= 0) {
                roles[existing] |= ROLE_TRIGGER;
            } else if (append_device(fd, devloc, ROLE_TRIGGER) < 0) {
                close(fd);
                closedir(dir);
                return -1;
            }
            candidates++;
            print_all("[INFO] '%s' can emit the stop trigger.\n", devloc, 0, print_flags);
        } else if (existing < 0) {
            close(fd);
        }
    }

    closedir(dir);
    return candidates;
}

static void usage(char *name)
{
    fprintf(stderr, "Usage: %s -d device... [-s trigger] [-p lockfile] [-v level] [-r x1,y1,x2,y2...] [-R ] [-W width] [-H height]\n", name);
    fprintf(stderr, "    -d: blocking device. Preset device id or a path specified device.\n");
    fprintf(stderr, "        Preset devices (0=Touchscreen, 1=Volume Down, 2=Volume Up, 3=Power Button)\n");
    fprintf(stderr, "        Specific device (/dev/input/eventX) \n\n");
    fprintf(stderr, "    -s: stop trigger.Preset device or a device specified event.\n");
    fprintf(stderr, "        Preset devices (0=Area Button, 1=Volume Down, 2=Volume Up, 3=Power Button)\n");
    fprintf(stderr, "        Specific event (/dev/input/eventX:<Type>,<Code>,<Value>)\n\n");
    fprintf(stderr, "    -p: single-instance lock file; a second invocation stops the owner.\n");
    fprintf(stderr, "    -v: verbosity level.(Errors=1, None=2, All=4) (Default=1)\n");
    fprintf(stderr, "    -r: rectangle.Comma seperated left bottom and right top corner point coordinates.\n");
    fprintf(stderr, "        -d 0 -s (1-3) -r x1,y1,x2,y2 : specifies partly blocking rectangle on the touchscreen.\n");
    fprintf(stderr, "        -d 0 -r x1,y1,x2,y2 -s 0 -r x1,y1,x2,y2 : specifies partly blocking.\n");
    fprintf(stderr, "        -d (1-3) -s 0 -r x1,y1,x2,y2 : specifies area button's rectangle.\n\n");
    fprintf(stderr, "    -R: reverse. Reverses blocking rectangle on the touchscreen.\n");
    fprintf(stderr, "    -W: screen width.\n");
    fprintf(stderr, "    -H: screen height.\n");
    fprintf(stderr, "    -h: print help.\n");
    fprintf(stderr, "\nblockevent for Android v%s\n", VERSION);
    fprintf(stderr, "https://github.com/nmelihsensoy/blockevent\n");
}

int main(int argc, char *argv[])
{
    int res;
    int c, i;
    Rect area;
    int n_devs = 0;
    int64_t prev = 0, now = 0;
    uint8_t flags = 0;
    Point touch_point = {0, 0};
    char *tmp_end_ptr;
    Rect v_button_area;
    uint8_t classes = 0;
    int screen_width = 0;
    int screen_height = 0;
    uint8_t tmp_flags = 0;
    char *tmp_area = NULL;
    char *tmp_event = NULL;
    char *given_devices[10]; // max 10 device
    uint8_t print_flags = 0;
    struct input_event event;
    char *trigger_dev = NULL;
    bool temp_reverse = false;
    char *tmp_v_button_area = NULL;
    char *instance_path = NULL;
    struct input_event trigger_event;
    int *area_coords[] = {&area.left_bottom.x, &area.left_bottom.y,
                            &area.right_top.x, &area.right_top.y};
    int *v_button_coords[] = {&v_button_area.left_bottom.x, &v_button_area.left_bottom.y,
                            &v_button_area.right_top.x, &v_button_area.right_top.y};

    i = 0;
    opterr = 0;
    do{
        c = getopt (argc, argv, "hd:s:v:W:H:Rr:p:");
        if (c == EOF)
            break;
        switch (c){
        case 'd':
            if (n_devs >= (int)(sizeof(given_devices) / sizeof(given_devices[0]))) {
                print_err("[ERR] too many blocking devices.\n", NULL, 0, PRINT_ERR);
                return 1;
            }
            given_devices[n_devs] = optarg;
            n_devs++;
            break;
        case 's':
            trigger_dev = optarg;
            flags |= ST_TRIGGER;
            break;
        case 'v':
            print_flags |= strtoul(optarg, NULL, 0);
            print_flags |= PRINT_ISSET;
            break;
        case 'p':
            instance_path = optarg;
            break;
        case 'W':
            screen_width = (int)strtol(optarg, &tmp_end_ptr, 0);
            if (tmp_end_ptr == optarg){
                usage(argv[0]);
                exit(1);
            }
            break;
        case 'H':
            screen_height = (int)strtol(optarg, &tmp_end_ptr, 0);
            if (tmp_end_ptr == optarg){
                usage(argv[0]);
                exit(1);
            }
            break;
        case 'r':
            if (i == 0){
                flags |= ST_TS_BLOCK_PARTLY;
                tmp_area = optarg;
                i++;
            }else{
                tmp_v_button_area = optarg;
            }
            break;
        case 'R':
            flags |= ST_REVERSE_BLOCK;
            break;
        case '?':
            if (optopt != 'R' || optopt != 'h')
            fprintf (stderr, "Option -%c requires an argument.\n", optopt);
            else if (isprint(optopt))
            fprintf (stderr, "Unknown option `-%c'.\n", optopt);
            else
            fprintf (stderr, "Unknown option character `\\x%x'.\n", optopt);
            return 1;
        case 'h':
            usage(argv[0]);
            exit(1);
        }
    }while (1);

    if (argc < 2) {
        usage(argv[0]);
        return 1;
    }

    // set default verbose level to PRINT_ERR
    if ((print_flags & PRINT_ISSET) == 0)
        print_flags |= PRINT_ERR;

    tmp_end_ptr = NULL;
    nfds = 0;
    signal(SIGINT, sig_handler);
    signal(SIGTERM, sig_handler);
    signal(SIGHUP, sig_handler);

    /* Toggle before device discovery so recovery never depends on current input nodes. */
    res = acquire_or_toggle_instance(instance_path, print_flags);
    if (res != 0)
        return res > 0 ? 2 : 1;
    if (instance_lock_fd >= 0 && atexit(release_instance_lock) != 0) {
        release_instance_lock();
        return 1;
    }

    if (flags & ST_TRIGGER){
        if (trigger_dev == NULL){
            return 1;
        }

        i = trigger_dev[0] - '0';
        if (i == 0 && trigger_dev[1] == '\0'){
            flags |= ST_VIRT_BUTTON;
            if (tmp_v_button_area == NULL && tmp_area != NULL){ // -d 0 -s 0 -r x,y,x,y
                flags &= ~ST_TS_BLOCK_PARTLY;
                if (parse_pixel_rect(tmp_area, v_button_coords, ",", screen_width, screen_height, print_flags) < 0)
                    return 1;
            }else{
                if (parse_pixel_rect(tmp_v_button_area, v_button_coords, ",", screen_width, screen_height, print_flags) < 0)
                    return 1;
            }
        }else if (i > 0 && i < PRESET_DEV_CNT && trigger_dev[1] == '\0'){
            trigger_event = dev_presets[i].event;
        }else{
            flags |= ST_CUSTOM_TRIGGER;
            trigger_dev = strtok(trigger_dev, ":"); // eventX:1,114,1 # get eventX
            tmp_event = strtok(NULL, ":"); // eventX:1,114,1 # get 1,114,1
            if (tmp_event == NULL){
                print_err("[ERR] it's mandatory to providing an event.\n", NULL, 0, print_flags);
                return 1;
            }
            tmp_event = strtok(tmp_event, ","); // parse event

            i=0;
            while (tmp_event != NULL) {
                if (i == 0){
                    trigger_event.type = strtol(tmp_event, NULL, 0);
                }else if(i == 1){
                    trigger_event.code = strtol(tmp_event, NULL, 0);
                }else if(i==2){
                    trigger_event.value = strtol(tmp_event, NULL, 0);
                }
                tmp_event = strtok(NULL, ",");
                i++;
            }
        }
    }

    for (i = 0; i < n_devs; i++){
        res = given_devices[i][0] - '0';
        if (res >= 0 && res < PRESET_DEV_CNT && given_devices[i][1] == '\0'){
            flags |= ST_SCAN;
            classes |= dev_presets[res].dev_type;
            print_all("[INFO] device added to the scanner.\n", NULL, i, print_flags);
        }else{
            tmp_flags = 0;
            tmp_flags |= DEV_ANY;
            res = open_device(given_devices[i], &tmp_flags, print_flags, false, ROLE_BLOCK);
            if (res < 0){
                return 1;
            }
        }
    }

    if (flags & ST_VIRT_BUTTON){
        if (screen_height == 0 || screen_width == 0){
            usage(argv[0]);
            return 1;
        }
    }

    if (flags & ST_TS_BLOCK_PARTLY){
        if (screen_height == 0 || screen_width == 0){
            usage(argv[0]);
            return 1;
        }

       if (parse_pixel_rect(tmp_area, area_coords, ",", screen_width, screen_height, print_flags) < 0){
           usage(argv[0]);
           return 1;
       }
    }

    // open manually entered device directly with 'DEV_ANY' flag and store it's pfds id
    if (flags & ST_CUSTOM_TRIGGER){
        tmp_flags = 0;
        tmp_flags |= DEV_ANY;
        res = open_device(trigger_dev, &tmp_flags, print_flags, true, ROLE_TRIGGER);
        if (res < 0)
            return 1;
    }

    // start scanning for preset devices
    if (flags & ST_SCAN){
        print_all("[INFO] Device scan has started with flag:'%lu'\n", NULL, flags, print_flags);
        res = scan_devices(classes, print_flags);
        if (res < 0){
            print_err("[ERR] Failed to open '%s'.\n", DEV_DIR, 0, print_flags);
            return 1;
        }else if(res > 0){
            print_err("[ERR] Scan couldn't find all devices. Mising device flag:'%d'.\n", NULL, res, print_flags);
            return 1;
        }
    }

    if ((flags & ST_TRIGGER) && !(flags & ST_CUSTOM_TRIGGER) &&
        !(flags & ST_VIRT_BUTTON)) {
        res = scan_trigger_candidates(trigger_event.code, print_flags);
        if (res <= 0) {
            print_err("[ERR] no input device provides the selected stop trigger.\n",
                NULL, 0, print_flags);
            close_devices(print_flags);
            return 1;
        }
    }

    if ((flags & (ST_VIRT_BUTTON | ST_TS_BLOCK_PARTLY)) &&
        dev_nid[ID_TOUCHSCREEN] < 0) {
        print_err("[ERR] partial/area mode requires a touchscreen blocking device.\n",
            NULL, 0, print_flags);
        close_devices(print_flags);
        return 1;
    }

    if (e_flag) {
        close_devices(print_flags);
        return 0;
    }

    for (i = 0; i < nfds; i++){
        if (!(roles[i] & ROLE_BLOCK))
            continue;

        if (ioctl(pfds[i].fd, EVIOCGRAB, 1) < 0){
            print_err("[ERR] '%s' couldn't get exclusive access.Aborted.\n", devs[i], 0, print_flags);
            close_devices(print_flags);
            return 1;
        }
    }

    if (flags & ST_TS_BLOCK_PARTLY){
        if (clone_device(dev_nid[ID_TOUCHSCREEN], DEV_TOUCHSCREEN, print_flags) < 0){
            close_devices(print_flags);
            return 1;
        }
    }

    if (e_flag) {
        close_devices(print_flags);
        return 0;
    }
    if (instance_path != NULL) {
        puts(READY_MESSAGE);
        fflush(stdout);
    }

    if ((flags & ST_TRIGGER) == 0 && (flags & ST_TS_BLOCK_PARTLY) == 0 && (flags & ST_VIRT_BUTTON) == 0){
        print_all("[INFO] (CTRL+C) to stop blocking.\n", NULL, 0, print_flags);
    }

    while(1){
        if (e_flag) break;
        /* A timeout closes the tiny check-to-poll signal race within a bounded delay. */
        res = poll(pfds, nfds, POLL_TIMEOUT_MS);
        if (res < 0) {
            if (errno == EINTR)
                continue;
            print_err("[ERR] input polling failed; releasing devices.\n", NULL, 0, print_flags);
            break;
        }
        if (res == 0)
            continue;

        for (i = 0; i < nfds; i++){
            if (pfds[i].revents){
                if (pfds[i].revents & (POLLERR | POLLHUP | POLLNVAL)) {
                    print_err("[ERR] '%s' became unavailable; releasing devices.\n",
                        devs[i], 0, print_flags);
                    e_flag = 1;
                    break;
                }
                if(pfds[i].revents & POLLIN) {
                    res = read(pfds[i].fd, &event, sizeof(event));
                    if(res < (int)sizeof(event)){
                        if (res < 0 && (errno == EAGAIN || errno == EINTR))
                            continue;
                        print_err("[ERR] '%s' couldn't get event.\n", devs[i], 0, print_flags);
                        e_flag = 1;
                        break;
                    }

                    if (print_flags & PRINT_ALL)
                        print_event(&event, i);

                    if ((flags & ST_TS_BLOCK_PARTLY) && i == dev_nid[ID_TOUCHSCREEN]){
                        set_point_from_event(&touch_point, &event);

                        temp_reverse = is_in_area(&touch_point, &area);
                        if (flags & ST_REVERSE_BLOCK)
                            temp_reverse = !temp_reverse;

                        if (temp_reverse)
                            continue;

                        write(uinp_fd, &event, sizeof(event));
                        if (event.type == EV_SYN)
                            usleep(1000);
                    }

                    if((flags & ST_VIRT_BUTTON) && i == dev_nid[ID_TOUCHSCREEN]){
                        set_point_from_event(&touch_point, &event);

                        if (event.code == BTN_TOUCH
                            && is_in_area(&touch_point, &v_button_area)
                            && event.value == 1
                            && detect_double_tap(&now, &prev, &event, 200000)) // 200 miliseconds
                                e_flag = 1;
                    }

                    if ((flags & ST_TRIGGER) && (roles[i] & ROLE_TRIGGER)) {
                        if (flags & ST_CUSTOM_TRIGGER) {
                            if (are_same_event(&trigger_event, &event))
                                e_flag = 1;
                        } else if (event.type == trigger_event.type &&
                            event.code == trigger_event.code && event.value > 0) {
                            e_flag = 1;
                        }
                    }
                }
            }
        }
    }

    close_devices(print_flags);
    return 0;
}

static void sig_handler(int signum)
{
    (void)signum;
    e_flag = 1;
}
