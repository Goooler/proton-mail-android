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

package ch.protonmail.android.contacts.groups.edit

import androidx.work.WorkManager
import ch.protonmail.android.api.ProtonMailApiManager
import ch.protonmail.android.data.ContactsRepository
import ch.protonmail.android.labels.data.LabelRepository
import ch.protonmail.android.labels.data.db.LabelEntity
import ch.protonmail.android.labels.data.mapper.LabelsMapper
import ch.protonmail.android.labels.data.model.Label
import ch.protonmail.android.labels.data.model.LabelId
import ch.protonmail.android.labels.data.model.LabelRequestBody
import ch.protonmail.android.labels.data.model.LabelResponse
import ch.protonmail.android.labels.data.model.LabelType
import ch.protonmail.android.worker.CreateContactGroupWorker
import com.birbit.android.jobqueue.JobManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ContactGroupEditCreateRepositoryTest {

    private val jobManager: JobManager = mockk(relaxed = true)

    private val workManager: WorkManager = mockk(relaxed = true)

    private val apiManager: ProtonMailApiManager = mockk(relaxed = true)

    private val contactsRepository: ContactsRepository = mockk(relaxed = true)

    private val labelsMapper: LabelsMapper = LabelsMapper()

    private val labelRepository: LabelRepository = mockk()

    private val createContactGroupWorker: CreateContactGroupWorker.Enqueuer = mockk(relaxed = true)

    private val repository = ContactGroupEditCreateRepository(
        jobManager, workManager, apiManager, contactsRepository, labelsMapper, createContactGroupWorker, labelRepository
    )

    private val testPath = "a/bpath"
    private val testParentId = "parentIdForTests"
    private val testType =  LabelType.CONTACT_GROUP

    private val testLabel = Label(
        id = "labelID",
        name = "name",
        color = "color",
        path = testPath,
        type = testType,
        notify = 0,
        order = 1,
        expanded = null,
        sticky = null,
        parentId = testParentId
    )

    private val testUserId = UserId("TestUserId")

    @Test
    fun whenEditContactGroupSucceedsThenSaveContactGroupAndContactEmailToTheDb() = runBlocking {
        // given
        val contactGroupId = "contact-group-id"
        val contactLabel = LabelEntity(
            LabelId(contactGroupId), testUserId, "name", "color", 1, testType, testPath, testParentId, 0, 0, 0
        )
        val labelResponse =
            LabelResponse(testLabel.copy(id = contactGroupId, type = LabelType.CONTACT_GROUP))
        coEvery { apiManager.updateLabel(any(), any(), any()) } returns ApiResult.Success(labelResponse)
        coEvery { labelRepository.saveLabel(contactLabel) } just Runs
        val updateLabelRequest = LabelRequestBody(
            name = "name",
            color = "color",
            type = testType.typeInt,
            notify = 0,
            parentId = testParentId,
            expanded = 0,
            sticky = 0
        )

        // when
        val apiResult = repository.editContactGroup(contactLabel, testUserId)

        // then
        assertNotNull(apiResult)
        coVerify { apiManager.updateLabel(testUserId, contactGroupId, updateLabelRequest) }
        coVerifyOrder {
            labelRepository.saveLabel(contactLabel)
        }
    }


    @Test
    fun whenEditContactGroupApiCallFailsThenCreateContactGroupWorkerIsCalled() = runBlocking {
        val contactGroupId = "contact-group-id"
        val contactLabel = LabelEntity(
            LabelId(contactGroupId),
            testUserId,
            "name",
            "color",
            0,
            testType,
            testPath,
            testParentId,
            0,
            0,
            0
        )
        val error = "Test API Error"
        val protonErrorData = ApiResult.Error.ProtonData(123, error)
        val apiResponse = ApiResult.Error.Http(123, error, protonErrorData)
        coEvery { apiManager.updateLabel(any(), any(), any()) } returns apiResponse

        val apiResult = repository.editContactGroup(contactLabel, testUserId)

        assert(apiResult is ApiResult.Error)
        verify { createContactGroupWorker.enqueue("name", "color", 0, 0, true, contactGroupId) }
    }

    @Test
    fun whenCreateContactGroupIsCalledCreateLabelCompletableApiGetsCalledWithTheRequestObject() =
        runBlocking {
            val contactGroupId = "contact-group-id"
            val contactLabel = LabelEntity(
                LabelId(contactGroupId),
                testUserId,
                "name",
                "color",
                0,
                testType,
                testPath,
                testParentId,
                0,
                0,
                0
            )
            val updateLabelRequest = labelsMapper.mapLabelEntityToRequestLabel(contactLabel)
            val labelResponse = LabelResponse(testLabel)
            coEvery { apiManager.createLabel(testUserId, any()) } returns ApiResult.Success(labelResponse)
            coEvery { labelRepository.saveLabel(any()) } just Runs

            repository.createContactGroup(contactLabel, testUserId)

            coVerify { apiManager.createLabel(testUserId, updateLabelRequest) }
        }

    @Test
    fun whenCreateContactGroupSucceedsThenSaveContactGroupToTheDb() = runBlocking {
        // given
        val contactLabel =
            LabelEntity(
                LabelId("labelID"),
                testUserId,
                "name",
                "color",
                1,
                LabelType.CONTACT_GROUP,
                testPath,
                testParentId,
                0,
                0,
                0
            )
        val labelResponse = LabelResponse(testLabel)
        coEvery { apiManager.createLabel(testUserId, any()) } returns ApiResult.Success(labelResponse)
        coEvery { labelRepository.saveLabel(contactLabel) } just Runs

        // when
        val apiResult = repository.createContactGroup(contactLabel, testUserId)

        // then
        assertNotNull(apiResult)
        assertEquals(testLabel, (apiResult as? ApiResult.Success)?.valueOrThrow?.label)
        coVerify { labelRepository.saveLabel(contactLabel) }
    }

    @Test
    fun whenCreateContactGroupApiCallFailsThenCreateContactGroupWorkerIsCalled() = runBlocking {
        val contactLabel =
            LabelEntity(
                LabelId(""), testUserId, "name", "color", 1, LabelType.CONTACT_GROUP, "a/b", "ParentId", 1,
                0, 0
            )
        val error = "Test API Error"
        val protonErrorData = ApiResult.Error.ProtonData(123, error)
        val apiResponse = ApiResult.Error.Http(123, error, protonErrorData)
        coEvery { apiManager.createLabel(testUserId, any()) } returns apiResponse

        val apiResult = repository.createContactGroup(contactLabel, testUserId)

        assertNotNull(apiResult)
        assert(apiResult is ApiResult.Error)
        verify { createContactGroupWorker.enqueue("name", "color", 1, 0, false, "") }
    }
}
