package org.jetbrains.ruby.codeInsight.types.signature

interface MethodInfo {
    val classInfo: ClassInfo
    val name: String
    val visibility: RVisibility
    val location: Location?

    data class Impl(override val classInfo: ClassInfo,
                    override val name: String,
                    override val visibility: RVisibility,
                    override val location: Location?) : MethodInfo
}

fun MethodInfo(classInfo: ClassInfo, name: String, visibility: RVisibility) = MethodInfo.Impl(classInfo, name, visibility, null)

fun MethodInfo(classInfo: ClassInfo, name: String, visibility: RVisibility, location: Location) = MethodInfo.Impl(classInfo, name, visibility, location)

fun MethodInfo(copy: MethodInfo) = with(copy) { MethodInfo.Impl(ClassInfo(classInfo), name, visibility, location) }

data class Location(val path: String, val lineno: Int)

enum class RVisibility constructor(val value: Byte, val presentableName: String) {
    PRIVATE(0, "PRIVATE"),
    PROTECTED(1, "PROTECTED"),
    PUBLIC(2, "PUBLIC");
}