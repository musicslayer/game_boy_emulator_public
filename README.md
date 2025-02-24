# Game Boy Emulator
A working Game Boy emulator.

This emulator achieves reasonably accurate emulation and performance while still having an easy-to-read code base. See the [Limitations](#limitations) section below for more details.

The goal of this project was to fulfill a childhood dream of successfully emulating a video game console.

## Installation Instructions
Clone this repository and run Main.java to start the emulator.

Inside Main.java, you can change which BIOS and ROM will be loaded (including ones that you provide on your own).

## Controls
The following key mapping is hardcoded into the emulator:
```
DPAD UP -> Up Arrow
DPAD DOWN -> Down Arrow
DPAD LEFT -> Left Arrow
DPAD RIGHT -> Right Arrow
BUTTON B -> Z Key
BUTTON A -> X Key
BUTTON SELECT -> Q Key
BUTTON START -> W Key
```

Note that the tech demo (pocket.gb) is non-interactive.

## BIOS and ROMS
To avoid any potential legal issues:
- For the BIOS, I use [Bootix](https://github.com/Hacktix/Bootix).
- For the ROMS, I have included an open-source port of [Evoland](https://github.com/flozz/evoland.gb) and a tech demo [Is That a Demo in Your Pocket?](https://archive.org/details/demo_is_that_a_demo_in_your_pocket_2015)

## Limitations
### ROM Support
The emulator supports ROMS that use the following memory bank controllers:
- No memory bank controller ("MBC0")
- MBC1 (Including MultiCart)
- MBC2
- MBC3
- MBC5

The emulator supports saving RAM to disk to emulate battery-backed RAM, but does not emulate other features such as the real time clock in MBC3.

### Accuracy
For some background context, a real Game Boy operates at 4194304 Hz, meaning that roughly every 23.84 nanoseconds (one T-Cycle), the CPU, APU, and PPU are clocked and perform an action/tick.
For the CPU specifically, the actions it performs take integer multiples of 4 T-Cycles, so we can refer to 4 T-Cycles as an M-Cycle.

My emulator tries to be as accurate as possible, but for practicality it has some compromises baked in:
- Instead of updating the display and the sound every T-Cycle, I emulate one frame's worth of instructions (70224 T-Cycles) and then display an image to the screen and play a sound. This is chosen to match the Game Boy's frame rate of roughly 59.7 frames per second.
- The APU and PPU do try to achieve T-Cycle level accuracy. For example, the PPU uses the exact T-Cycle to know when to fire STAT interrupts. In contrast, the CPU merely has instruction-level accuracy. If it encounters an instruction, it will wait the appropriate number of T-Cycles before executing the next instruction, but it does not guarantee which T-Cycle or even M-Cycle the individual actions will occur on. This is mostly relevant for accessing memory that is shared with the APU and PPU, as the exact timing of memory accesses could affect what is shown on the screen or played through the speaker. In practice, this usually amounts to small graphical inaccuracies.

Also, the emulator doesn't accurately emulate memory conflicts such as accessing Wave RAM while sound is playing, or accessing OAM and VRAM during certain rendering modes.

### Performance
The only performance metric considered is the amount of time it takes to execute one frame's worth of T-Cycles. A frame should take around 16.7 milliseconds, so anything at or below this time is ideal (if a frame is quicker than this, the emulator will wait to maintain accurate timing).

When running the tech demo, a relatively demanding program, the emulator mostly stays below 16.7 milliseconds, but occasionally jumps up to 17-18 millisecond frames and has a worst-case frame of 20.5 milliseconds.

## References
### Game Boy Internals
In no particular order, listed below are various places where I have gotten information about the inner workings of the Game Boy.

- [Pan Docs](https://gbdev.io/pandocs/)
- [Megan Sullivan's Opcode Reference](https://meganesu.github.io/generate-gb-opcodes/)
- [gekkio's "Game Boy: Complete Technical Reference"](https://gekkio.fi/files/gb-docs/gbctr.pdf)
- [GbdevWiki "Gameboy sound hardware"](https://gbdev.gg8.se/wiki/articles/Gameboy_sound_hardware)
- [The Cycle-Accurate Game Boy Docs](https://github.com/AntonioND/giibiiadvance/blob/master/docs/TCAGBD.pdf)
- [Hacktix's Github (Various Repositories)](https://github.com/Hacktix/)
- [The Ultimate Game Boy Talk](https://www.youtube.com/watch?v=HyzD8pNlpwI)
- [Nitty Gritty Gameboy Cycle Timing](http://blog.kevtris.org/blogfiles/Nitty%20Gritty%20Gameboy%20VRAM%20Timing.txt)
- [The Gameboy Emulator Development Guide](https://hacktix.github.io/GBEDG/)

### Tests
The following tests were used to aid in the development of this emulator:
- [Blargg](https://github.com/retrio/gb-test-roms)
- [Mooneye](https://github.com/Gekkio/mooneye-test-suite)
- [Mealybug Tearoom](https://github.com/mattcurrie/mealybug-tearoom-tests)
- [dmg-acid2](https://github.com/mattcurrie/dmg-acid2)
- [firstwhite](https://github.com/torch2424/wasmboy/issues/203)
- [Scribbltests](https://github.com/Hacktix/scribbltests)
