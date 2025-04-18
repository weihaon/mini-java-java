	.text
.global main
main:
Main_main:
	pushq %rbp
	movq %rsp, %rbp
	subq $8, %rsp
	movq $1, %rax
	pushq %rax
	movq $1, %rax
	popq %rcx
	cqto
	idivq %rcx
	movq %rax, -8(%rbp)
	movq $1, %rax
	pushq %rax
	movq -8(%rbp), %rax
	popq %rdx
	cmpq %rdx, %rax
	sete %al
	movzbq %al, %rax
	testq %rax, %rax
	je L4
	leaq string0(%rip), %rax
	pushq %rax
	leaq string1(%rip), %rax
	testq %rax, %rax
	jne L6
	movq $null_error, %rdi
	call puts
	movq $1, %rdi
	call exit
L6:
	pushq %rax
	movq $String_descriptor, %rcx
	call *8(%rcx)
	addq $16, %rsp
	testq %rax, %rax
	je L4
	movq $1, %rax
	jmp L5
L4:
	movq $0, %rax
L5:
	testq %rax, %rax
	je L2
	leaq string2(%rip), %rax
	pushq %rax
	leaq string2(%rip), %rax
	testq %rax, %rax
	jne L7
	movq $null_error, %rdi
	call puts
	movq $1, %rdi
	call exit
L7:
	pushq %rax
	movq $String_descriptor, %rcx
	call *8(%rcx)
	addq $16, %rsp
	testq %rax, %rax
	je L2
	movq $1, %rax
	jmp L3
L2:
	movq $0, %rax
L3:
	testq %rax, %rax
	je L0
	leaq string3(%rip), %rax
	movq %rax, %rsi
	movq $string_format, %rdi
	movq $0, %rax
	call printf
	movq $0, %rax
	jmp L1
L0:
L1:
	movq $0, %rax
	leave
	ret
my_malloc:
	pushq %rbp
	movq %rsp, %rbp
	andq $-16, %rsp
	call malloc
	movq %rbp, %rsp
	popq %rbp
	ret
instanceof:
	pushq %rbp
	movq %rsp, %rbp
	testq %rdi, %rdi
	je instanceof_false
	movq (%rdi), %rax
instanceof_loop:
	cmpq %rsi, %rax
	je instanceof_true
	movq (%rax), %rax
	testq %rax, %rax
	jnz instanceof_loop
instanceof_false:
	movq $0, %rax
	leave
	ret
instanceof_true:
	movq $1, %rax
	leave
	ret
checkcast:
	pushq %rbp
	movq %rsp, %rbp
	testq %rdi, %rdi
	je checkcast_ok
	call instanceof
	testq %rax, %rax
	jnz checkcast_ok
	movq $cast_error, %rdi
	call puts
	movq $1, %rdi
	call exit
checkcast_ok:
	movq %rdi, %rax
	leave
	ret
String_equals:
	pushq %rbp
	movq %rsp, %rbp
	movq 16(%rbp), %rdi
	testq %rdi, %rdi
	jne L8
	movq $null_error, %rdi
	call puts
	movq $1, %rdi
	call exit
L8:
	movq 24(%rbp), %rsi
	testq %rsi, %rsi
	jne L9
	movq $0, %rax
	leave
	ret
L9:
	movq %rdi, %rsi
	movq 24(%rbp), %rdi
	call strcmp
	testq %rax, %rax
	jne L10
	movq $1, %rax
	leave
	ret
L10:
	movq $0, %rax
	leave
	ret
	.data
Object_descriptor:
	.quad 0
Main_descriptor:
	.quad Object_descriptor
	.quad Main_main
String_descriptor:
	.quad Object_descriptor
	.quad String_equals
string0:
	.string "\n \" /* */ // qsdq#sds\n "
string1:
	.string "\n \" /* */ // qsdq#sds\n "
string2:
	.string "dfsdf/* dfsdf */ fsdf // sdfsdf "
string3:
	.string "ok\n"
cast_error:
	.string "Runtime error: invalid cast"
null_error:
	.string "Runtime error: null pointer dereference"
int_format:
	.string "%d"
string_format:
	.string "%s"
true_str:
	.string "true"
false_str:
	.string "false"
