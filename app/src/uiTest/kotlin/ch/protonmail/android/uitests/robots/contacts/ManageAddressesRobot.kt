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

package ch.protonmail.android.uitests.robots.contacts

import ch.protonmail.android.R
import me.proton.core.test.android.instrumented.CoreRobot

/**
 * [ManageAddressesRobot] class contains actions and verifications for Adding a Contact to Group.
 */
class ManageAddressesRobot : CoreRobot {

    fun addContactToGroup(withEmail: String): AddContactGroupRobot = selectAddress(withEmail).done()

    private fun selectAddress(email: String): ManageAddressesRobot {
        recyclerView
            .withId(contactsRecyclerView)
            .waitUntilPopulated()
            .onHolderItem(ContactsMatchers.withContactEmailInManageAddressesView(email))
            .click()
        return this
    }

    private fun done(): AddContactGroupRobot {
        view.withId(R.id.action_save).click()
        return AddContactGroupRobot()
    }

    companion object {
        const val contactsRecyclerView = R.id.contactEmailsRecyclerView
    }
}
