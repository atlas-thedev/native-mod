# Tiny XTEST driver for the Xvfb display: python3 x.py move X Y | click X Y | key NAME | type TEXT | shot FILE
import sys, time, subprocess
from Xlib import X, XK, display
from Xlib.ext import xtest
d = display.Display()
def move(x, y): xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y)); d.sync()
def btn(b=1): xtest.fake_input(d, X.ButtonPress, b); d.sync(); time.sleep(0.08); xtest.fake_input(d, X.ButtonRelease, b); d.sync()
def key(name):
    ks = XK.string_to_keysym(name); code = d.keysym_to_keycode(ks)
    shift = name.isupper() and len(name) == 1
    if shift: xtest.fake_input(d, X.KeyPress, d.keysym_to_keycode(XK.XK_Shift_L))
    xtest.fake_input(d, X.KeyPress, code); d.sync(); time.sleep(0.05); xtest.fake_input(d, X.KeyRelease, code)
    if shift: xtest.fake_input(d, X.KeyRelease, d.keysym_to_keycode(XK.XK_Shift_L))
    d.sync(); time.sleep(0.05)
SPECIAL = {' ': 'space', '!': 'exclam', '?': 'question', '.': 'period', ',': 'comma'}
a = sys.argv[1:]
while a:
    c = a.pop(0)
    if c == 'move': move(a.pop(0), a.pop(0))
    elif c == 'click': move(a.pop(0), a.pop(0)); time.sleep(0.15); btn(1)
    elif c == 'scroll': n = int(a.pop(0)); [btn(4 if n > 0 else 5) for _ in range(abs(n))]
    elif c == 'key': key(a.pop(0))
    elif c == 'type':
        for ch in a.pop(0): key(SPECIAL.get(ch, ch))
    elif c == 'sleep': time.sleep(float(a.pop(0)))
    elif c == 'shot': subprocess.run(['import', '-window', 'root', a.pop(0)])
