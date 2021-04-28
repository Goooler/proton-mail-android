/*
 * Copyright (c) 2020 Proton Technologies AG
 * 
 * This file is part of ProtonMail.
 * 
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.uitests.robots.mailbox.composer

import android.widget.ListView
import androidx.annotation.IdRes
import androidx.appcompat.widget.AppCompatImageButton
import ch.protonmail.android.R
import ch.protonmail.android.uitests.testsHelper.MockAddAttachmentIntent
import ch.protonmail.android.uitests.testsHelper.StringUtils.quantityStringFromResource
import me.proton.core.test.android.instrumented.CoreRobot
import org.hamcrest.CoreMatchers.anything

/**
 * Class represents Message Attachments.
 */
open class MessageAttachmentsRobot : CoreRobot {

    fun addImageCaptureAttachment(@IdRes drawable: Int): ComposerRobot =
        mockCameraImageCapture(drawable).navigateUpToComposerView()

    fun addTwoImageCaptureAttachments(
        @IdRes firstDrawable: Int,
        @IdRes secondDrawable: Int
    ): ComposerRobot =
        mockCameraImageCapture(firstDrawable)
            .mockCameraImageCapture(secondDrawable)
            .navigateUpToComposerView()

    fun addFileAttachment(@IdRes drawable: Int): ComposerRobot =
        mockFileAttachment(drawable).navigateUpToComposerView()

    fun removeLastAttachment(): MessageAttachmentsRobot {
        listView
            .onListItem(anything())
            .inAdapter(view.instanceOf(ListView::class.java))
            .atPosition(0)
            .onChild(view.withId(R.id.remove))
            .click()
        view.withText(R.string.no_attachments).wait().checkDisplayed()
        return MessageAttachmentsRobot()
    }

    fun removeOneOfTwoAttachments(): MessageAttachmentsRobot {
        val oneAttachmentString = quantityStringFromResource(R.plurals.attachments, 1)
        listView
            .onListItem(anything())
            .inAdapter(view.instanceOf(ListView::class.java))
            .atPosition(0)
            .onChild(view.withId(R.id.remove))
            .click()
        view.withText(oneAttachmentString).wait().checkDisplayed()
        return MessageAttachmentsRobot()
    }

    fun navigateUpToComposerView(): ComposerRobot {
        view.isDescendantOf(view.withId(R.id.toolbar)).instanceOf(AppCompatImageButton::class.java).click()
        return ComposerRobot()
    }

    private fun mockCameraImageCapture(@IdRes drawableId: Int): MessageAttachmentsRobot {
        view.withId(takePhotoIconId).wait()
        MockAddAttachmentIntent.mockCameraImageCapture(takePhotoIconId, drawableId)
        return this
    }

    private fun mockFileAttachment(@IdRes drawable: Int): MessageAttachmentsRobot {
        view.withId(addAttachmentIconId).wait()
        MockAddAttachmentIntent.mockChooseAttachment(addAttachmentIconId, drawable)
        return this
    }

    companion object {
        private const val takePhotoIconId = R.id.take_photo
        private const val addAttachmentIconId = R.id.attach_file
    }
}
