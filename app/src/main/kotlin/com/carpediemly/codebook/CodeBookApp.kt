package com.carpediemly.codebook

import android.app.Application

/**
 * 全局 Application。目前只作为单一实例入口，后续承载自动锁定与内存清理的宿主。
 */
class CodeBookApp : Application()
