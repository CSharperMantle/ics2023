#ifdef NDEBUG
#undef NDEBUG
#endif

#include <iostream>
#include <cstdlib>
#include <cassert>
#include "Vtop.h"
#include "verilated.h"


int main(int argc, char *argv[]) {
    VerilatedContext ctx{};
    ctx.commandArgs(argc, argv);
    Vtop top{&ctx};
    while (!ctx.gotFinish()) {
        const auto a = std::rand() & 1;
        const auto b = std::rand() & 1;
        top.a = a;
        top.b = b;
        top.eval();
        std::cout << "a = " << a << ", b = " << b << ", f = " << (int)top.f << '\n';
        assert(top.f == (a ^ b));
    }
    return 0;
}
