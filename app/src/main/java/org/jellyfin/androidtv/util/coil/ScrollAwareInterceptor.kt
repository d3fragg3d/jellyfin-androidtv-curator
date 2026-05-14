package org.jellyfin.androidtv.util.coil

import coil3.intercept.Interceptor
import coil3.request.ImageResult

class ScrollAwareInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        ScrollStateManager.awaitIdle()
        return chain.proceed()
    }
}
