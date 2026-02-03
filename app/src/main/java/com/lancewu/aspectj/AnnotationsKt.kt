package com.lancewu.aspectj

/**
 * Created by LanceWu on 2022/12/19
 *
 * 注解测试
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class AOPLog(val value: String = "AOPLog")

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Permissions(val value: Array<String>)