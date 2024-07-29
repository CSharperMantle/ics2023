# PA2 必答题

## 2. RTFSC(2)

**执行指令的流程:** `cpu_exec()` -> `execute()` -> `exec_once()` -> `isa_exec_once()` -> `inst_fetch()`, `decode_exec()`

## 5. 输入输出

### 编译与链接

**`static inline`:** `static`表示符号具有internal linkage, 即仅在本编译单元可见. `inline`提示编译器进行内联链接, 不产生显式的符号. 链接成功的前提是没有重复符号.

**文件级`volatile static`:** 在每个翻译单元产生产生一份独立的internal linkage符号, 时常被优化. 使用`__attribute__((used))`可以防止被优化, 便于观察bss段中的多个拷贝.

