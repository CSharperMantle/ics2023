#ifdef NDEBUG
#undef NDEBUG
#endif

#include <iostream>
#include <cstdlib>
#include <cassert>
#include <nvboard.h>
#include "Vtop.h"
#include "verilated.h"

extern void nvboard_bind_all_pins(Vtop* top);

int main(int argc, char *argv[]) {
    VerilatedContext ctx{};
    ctx.commandArgs(argc, argv);
    Vtop top{&ctx};
    nvboard_bind_all_pins(&top);
    nvboard_init();
    while (!ctx.gotFinish()) {
        top.eval();
        nvboard_update();
    }
    nvboard_quit();
    return 0;
}
