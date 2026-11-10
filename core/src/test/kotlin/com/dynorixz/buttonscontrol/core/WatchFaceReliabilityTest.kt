package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class WatchFaceReliabilityTest {
    private val ids = listOf("watch.host:id/item_watchface", "watch.host:id/watchface_overlay", "watch.host:id/koru_surfaceview_container")
    private val face = WindowSnapshot("watch.host", "Frame", "Frame", ids, 4, 1, true, "a".repeat(64), true, true)
    private fun verdict(w: WindowSnapshot = face, s: FaceSignature? = null) = WatchFacePolicy.evaluate(w,s,true,false,false)
    @Test fun systemHomeWithVisibleFaceRecognizedWithoutCalibration() { assertTrue(verdict().onFace) }
    @Test fun thirdPartyFaceInSameHostRecognized() { assertTrue(verdict(face.copy(topology="b".repeat(64))).onFace) }
    @Test fun offscreenWatchFaceInPagerIsRejected() { assertFalse(verdict(face.copy(faceSurfaceVisible=false)).onFace) }
    @Test fun regularAppCannotImpersonateHomeUsingIdsAlone() { assertFalse(verdict(face.copy(trustedHome=false)).onFace) }
    @Test fun appDrawerOverFaceRejected() { assertFalse(verdict(face.copy(viewIds=ids+"watch.host:id/app_drawer_container")).onFace) }
    @Test fun notificationsOverFaceRejected() { assertFalse(verdict(face.copy(viewIds=ids+"watch.host:id/notification_stack")).onFace) }
    @Test fun quickSettingsOverFaceRejected() { assertFalse(verdict(face.copy(viewIds=ids+"watch.host:id/quick_settings_container")).onFace) }
    @Test fun tileOverFaceRejected() { assertFalse(verdict(face.copy(viewIds=ids+"watch.host:id/tile_pager")).onFace) }
    @Test fun recentsNeverCalibratesAsFace() {
        assertFalse(WatchFacePolicy.canCalibrate(face.copy(packageName="com.samsung.android.apps.wearable.recent",viewIds=listOf("recent:id/recent_list_container"))))
    }
    @Test fun invalidOldRecentsProfileDoesNotPreventAutomaticRecognition() {
        val old=FaceSignature("com.samsung.android.apps.wearable.recent","Frame","Frame",listOf("recent:id/recent_list_container"),"a".repeat(64))
        assertTrue(verdict(s=old).onFace)
        assertFalse(verdict(face.copy(packageName=old.packageName,viewIds=old.viewIds),old).onFace)
    }
    @Test fun plainContentIdCannotCalibrate() { assertFalse(WatchFacePolicy.canCalibrate(face.copy(viewIds=listOf("android:id/content")))) }
    @Test fun trustedHostStillRequiresMultipleFaceAnchors() { assertFalse(verdict(face.copy(viewIds=ids.take(1))).onFace) }
    @Test fun calibratedGenericFaceIgnoresGeometryAndDynamicChildren() {
        val s=FaceSignature("third.party", "FaceActivity","Frame",listOf("third.party:id/face","third.party:id/changing_complication"),"a".repeat(64))
        val w=face.copy(packageName=s.packageName,className=s.className,rootClass=s.rootClass,viewIds=listOf("third.party:id/face"),trustedHome=false,topology="b".repeat(64))
        assertTrue(verdict(w,s).onFace)
    }
    @Test fun notificationDotDoesNotInvalidateFace() { assertTrue(verdict(face.copy(viewIds=ids+"watch.host:id/sec_unread_dot")).onFace) }
}
