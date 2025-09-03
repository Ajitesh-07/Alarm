# Alarm

A compact Android alarm app with Material3 dynamic theming, reliable alarm scheduling, a small stopwatch, and persistent storage via Room.

---

## Key features

1. **Dynamic Material3 colors** — Adapts the app’s color scheme to the user/device palette for a native, accessible look.

2. **Multiple alarms with flexible controls** — Create multiple alarms, set repeat on specific weekdays, edit, disable, or delete anytime. Each alarm supports a custom ringtone and configurable snooze duration.

3. **Room-backed persistence** — Alarms are stored in a Room database so they survive app restarts and device reboots.

4. **Compact stopwatch** — Lightweight stopwatch with start/stop/lap/reset for quick timing tasks.

---

## Quick debug command

To inspect scheduled system alarms (Windows):

```powershell
adb shell dumpsys alarm | findstr "RTC_WAKEUP"
```

---