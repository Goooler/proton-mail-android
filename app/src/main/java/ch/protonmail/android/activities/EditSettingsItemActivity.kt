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
package ch.protonmail.android.activities

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.children
import butterknife.OnClick
import ch.protonmail.android.R
import ch.protonmail.android.activities.settings.BaseSettingsActivity
import ch.protonmail.android.activities.settings.SettingsEnum
import ch.protonmail.android.adapters.swipe.SwipeAction
import ch.protonmail.android.api.segments.event.AlarmReceiver
import ch.protonmail.android.core.Constants.Prefs.PREF_HYPERLINK_CONFIRM
import ch.protonmail.android.core.ProtonMailApplication
import ch.protonmail.android.events.AuthStatus
import ch.protonmail.android.events.LogoutEvent
import ch.protonmail.android.events.SettingsChangedEvent
import ch.protonmail.android.jobs.UpdateSettingsJob
import ch.protonmail.android.prefs.SecureSharedPreferences
import ch.protonmail.android.uiModel.SettingsItemUiModel
import ch.protonmail.android.utils.UiUtil
import ch.protonmail.android.utils.extensions.isValidEmail
import ch.protonmail.android.utils.extensions.showToast
import ch.protonmail.android.utils.moveToLogin
import ch.protonmail.android.views.CustomFontEditText
import com.google.gson.Gson
import com.squareup.otto.Subscribe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_edit_settings_item.*
import me.proton.core.util.android.sharedpreferences.set
import me.proton.core.util.kotlin.EMPTY_STRING
import me.proton.core.util.kotlin.takeIfNotEmpty
import timber.log.Timber

// region constants
const val EXTRA_SETTINGS_ITEM_TYPE = "EXTRA_SETTINGS_ITEM_TYPE"
const val EXTRA_SETTINGS_ITEM_VALUE = "EXTRA_SETTINGS_ITEM_VALUE"
// endregion

enum class SettingsItem {
    DISPLAY_NAME_AND_SIGNATURE,
    PRIVACY,
    LABELS_AND_FOLDERS,
    SWIPE,
    PUSH_NOTIFICATIONS,
    CONNECTIONS_VIA_THIRD_PARTIES,
    COMBINED_CONTACTS,
    RECOVERY_EMAIL,
    AUTO_DOWNLOAD_MESSAGES,
    BACKGROUND_SYNC
}

@AndroidEntryPoint
class EditSettingsItemActivity : BaseSettingsActivity() {

    private val mailSettings by lazy {
        checkNotNull(userManager.getCurrentUserMailSettingsBlocking())
    }
    private var settingsItemType: SettingsItem = SettingsItem.DISPLAY_NAME_AND_SIGNATURE
    private var settingsItemValue: String? = null
    private var title: String? = null
    private var recoveryEmailValue: String? = null
    private var actionBarTitle: Int = -1
    private var initializedRemote = false
    private var initializedEmbedded = false

    private val isValidNewConfirmEmail: Boolean
        get() {
            val newRecoveryEmail = newRecoveryEmail!!.text.toString().trim()
            val newConfirmRecoveryEmail = newRecoveryEmailConfirm!!.text.toString().trim()
            return if (newRecoveryEmail.isEmpty() && newConfirmRecoveryEmail.isEmpty()) {
                true
            } else newRecoveryEmail == newConfirmRecoveryEmail && newRecoveryEmail.isValidEmail()
        }

    override fun getLayoutId(): Int = R.layout.activity_edit_settings_item

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        settingsItemType = intent.getSerializableExtra(EXTRA_SETTINGS_ITEM_TYPE) as SettingsItem
        settingsItemValue = intent.getStringExtra(EXTRA_SETTINGS_ITEM_VALUE)

        mSnackLayout = findViewById(R.id.layout_no_connectivity_info)

        val oldSettings =
            arrayOf(SettingsItem.RECOVERY_EMAIL, SettingsItem.AUTO_DOWNLOAD_MESSAGES, SettingsItem.BACKGROUND_SYNC)

        if (settingsItemType !in oldSettings) {
            val jsonSettingsListResponse =
                resources.openRawResource(R.raw.edit_settings_structure).bufferedReader().use { it.readText() }

            val gson = Gson()
            val settingsUiList =
                gson.fromJson(jsonSettingsListResponse, Array<Array<SettingsItemUiModel>>::class.java).asList()
            setUpSettingsItems(settingsUiList[settingsItemType.ordinal].asList())
        }

        renderViews()

        if (actionBar != null && actionBarTitle > 0) {
            actionBar.setTitle(actionBarTitle)
        }
    }


    override fun onResume() {
        super.onResume()
        renderViews()
    }

    override fun onStop() {
        super.onStop()
        initializedRemote = true
        initializedEmbedded = true
        enableFeatureSwitch.setOnCheckedChangeListener(null)
        setToggleListener(SettingsEnum.LINK_CONFIRMATION, null)
        setToggleListener(SettingsEnum.PREVENT_SCREENSHOTS, null)
        setToggleListener(SettingsEnum.SHOW_REMOTE_IMAGES, null)
        setToggleListener(SettingsEnum.SHOW_EMBEDDED_IMAGES, null)
    }

    override fun renderViews() {

        when (settingsItemType) {
            SettingsItem.RECOVERY_EMAIL -> {
                settingsRecyclerViewParent.visibility = View.GONE
                recoveryEmailValue = settingsItemValue
                currentRecoveryEmail.setText(recoveryEmailValue?.takeIfNotEmpty() ?: getString(R.string.not_set))
                recoveryEmailParent.visibility = View.VISIBLE
                header.visibility = View.GONE
                title = getString(R.string.edit_notification_email)
                actionBarTitle = R.string.recovery_email
            }
            SettingsItem.DISPLAY_NAME_AND_SIGNATURE -> {

                selectedAddress = checkNotNull(user.addresses.primary)
                val (newAddressId, currentSignature) = selectedAddress.id to selectedAddress.signature?.s

                if (mDisplayName.isNotEmpty()) {
                    setValue(SettingsEnum.DISPLAY_NAME, mDisplayName)
                }

                setEditTextListener(SettingsEnum.DISPLAY_NAME) {
                    var newDisplayName = (it as CustomFontEditText).text.toString()

                    val containsBannedChars = newDisplayName.matches(".*[<>].*".toRegex())
                    if (containsBannedChars) {
                        showToast(R.string.display_name_banned_chars, Toast.LENGTH_SHORT, Gravity.CENTER)
                        val primaryAddress = checkNotNull(user.addresses.primary)
                        newDisplayName = primaryAddress.displayName?.s
                            ?: primaryAddress.email.s
                    }


                    val displayChanged = newDisplayName != mDisplayName

                    if (displayChanged) {
                        legacyUser.displayName = newDisplayName
                        legacyUser.save()
                        user = legacyUser.toNewUser()

                        mDisplayName = newDisplayName

                        val job = UpdateSettingsJob(
                            displayChanged = displayChanged,
                            newDisplayName = newDisplayName,
                            addressId = newAddressId?.s ?: EMPTY_STRING
                        )
                        mJobManager.addJobInBackground(job)
                    }
                }

                if (!currentSignature.isNullOrEmpty()) {
                    setValue(SettingsEnum.SIGNATURE, currentSignature)
                }
                setEnabled(SettingsEnum.SIGNATURE, legacyUser.isShowSignature)


                val currentMobileSignature = legacyUser.mobileSignature
                if (!currentMobileSignature.isNullOrEmpty()) {
                    Timber.v("set mobileSignature $currentMobileSignature")
                    setValue(SettingsEnum.MOBILE_SIGNATURE, currentMobileSignature)
                }
                if (legacyUser.isPaidUserSignatureEdit) {
                    setEnabled(SettingsEnum.MOBILE_SIGNATURE, legacyUser.isShowMobileSignature)
                } else {
                    setEnabled(SettingsEnum.MOBILE_SIGNATURE, true)
                    setSettingDisabled(
                        SettingsEnum.MOBILE_SIGNATURE,
                        true,
                        getString(R.string.mobile_signature_is_premium)
                    )
                }

                setEditTextListener(SettingsEnum.SIGNATURE) {
                    val newSignature = (it as CustomFontEditText).text.toString()
                    val isSignatureChanged = newSignature != currentSignature

                    legacyUser.save()

                    if (isSignatureChanged) {
                        val job = UpdateSettingsJob(
                            signatureChanged = isSignatureChanged,
                            newSignature = newSignature,
                            addressId = newAddressId?.s ?: EMPTY_STRING
                        )
                        mJobManager.addJobInBackground(job)
                    }
                }

                setToggleListener(SettingsEnum.SIGNATURE) { _: View, isChecked: Boolean ->
                    legacyUser.isShowSignature = isChecked
                    legacyUser.save()
                }

                setEditTextListener(SettingsEnum.MOBILE_SIGNATURE) {
                    val newMobileSignature = (it as CustomFontEditText).text.toString()
                    val isMobileSignatureChanged = newMobileSignature != currentMobileSignature

                    if (isMobileSignatureChanged) {
                        legacyUser.mobileSignature = newMobileSignature
                        legacyUser.save()
                    }
                }

                setEditTextChangeListener(SettingsEnum.MOBILE_SIGNATURE) { newMobileSignature ->
                    Timber.v("text change save mobileSignature $newMobileSignature")
                    legacyUser.mobileSignature = newMobileSignature
                    legacyUser.save()
                }

                setToggleListener(SettingsEnum.MOBILE_SIGNATURE) { _: View, isChecked: Boolean ->
                    legacyUser.isShowMobileSignature = isChecked
                    legacyUser.save()
                }

                actionBarTitle = R.string.display_name_n_signature
            }
            SettingsItem.PRIVACY -> {

                mAutoDownloadGcmMessages = legacyUser.isGcmDownloadMessageDetails
                setValue(
                    SettingsEnum.AUTO_DOWNLOAD_MESSAGES,
                    if (mAutoDownloadGcmMessages) getString(R.string.enabled) else getString(R.string.disabled)
                )

                mBackgroundSyncValue = legacyUser.isBackgroundSync
                setValue(
                    SettingsEnum.BACKGROUND_REFRESH,
                    if (mBackgroundSyncValue) getString(R.string.enabled) else getString(R.string.disabled)
                )
                setEnabled(
                    SettingsEnum.LINK_CONFIRMATION,
                    sharedPreferences!!.getBoolean(PREF_HYPERLINK_CONFIRM, true)
                )

                setToggleListener(SettingsEnum.LINK_CONFIRMATION) { view: View, isChecked: Boolean ->
                    val prefs = checkNotNull(sharedPreferences)
                    if (view.isPressed && isChecked != prefs.getBoolean(PREF_HYPERLINK_CONFIRM, true))
                        prefs[PREF_HYPERLINK_CONFIRM] = isChecked
                }

                setEnabled(SettingsEnum.PREVENT_SCREENSHOTS, legacyUser.isPreventTakingScreenshots)
                setToggleListener(SettingsEnum.PREVENT_SCREENSHOTS) { view: View, isChecked: Boolean ->
                    if (view.isPressed && isChecked != legacyUser.isPreventTakingScreenshots) {
                        legacyUser.isPreventTakingScreenshots = isChecked
                        val infoSnack =
                            UiUtil.showInfoSnack(mSnackLayout, this, R.string.changes_affected_after_closing)
                        infoSnack.show()
                        legacyUser.save()
                    }
                }

                setEnabled(SettingsEnum.SHOW_REMOTE_IMAGES, mailSettings.showImagesFrom.includesRemote())
                setToggleListener(SettingsEnum.SHOW_REMOTE_IMAGES) { view: View, isChecked: Boolean ->
                    if (view.isPressed && isChecked != mailSettings.showImagesFrom.includesRemote()) {
                        initializedRemote = false
                    }

                    if (!initializedRemote) {
                        mailSettings.showImagesFrom = mailSettings.showImagesFrom.toggleRemote()

                        mailSettings.saveBlocking(SecureSharedPreferences.getPrefsForUser(this, user.id))
                        val job = UpdateSettingsJob()
                        mJobManager.addJobInBackground(job)
                    }
                }

                setEnabled(SettingsEnum.SHOW_EMBEDDED_IMAGES, mailSettings.showImagesFrom.includesEmbedded())
                setToggleListener(SettingsEnum.SHOW_EMBEDDED_IMAGES) { view: View, isChecked: Boolean ->
                    if (view.isPressed && isChecked != mailSettings.showImagesFrom.includesEmbedded()) {
                        initializedEmbedded = false
                    }

                    if (!initializedEmbedded) {
                        mailSettings.showImagesFrom = mailSettings.showImagesFrom.toggleEmbedded()

                        mailSettings.saveBlocking(SecureSharedPreferences.getPrefsForUser(this, user.id))
                        val job = UpdateSettingsJob()
                        mJobManager.addJobInBackground(job)
                    }
                }


                actionBarTitle = R.string.privacy
            }
            SettingsItem.AUTO_DOWNLOAD_MESSAGES -> {
                settingsRecyclerViewParent.visibility = View.GONE
                featureTitle.text = getString(R.string.auto_download_messages_title)
                enableFeatureSwitch.isChecked = legacyUser.isGcmDownloadMessageDetails

                enableFeatureSwitch.setOnCheckedChangeListener { view, isChecked ->

                    val gcmDownloadDetailsChanged = legacyUser.isGcmDownloadMessageDetails != isChecked
                    if (view.isPressed && gcmDownloadDetailsChanged) {
                        legacyUser.isGcmDownloadMessageDetails = isChecked
                        legacyUser.save()
                    }
                }
                descriptionParent.visibility = View.VISIBLE
                description.text = getString(R.string.auto_download_messages_subtitle)
                actionBarTitle = R.string.auto_download_messages_title
            }
            SettingsItem.BACKGROUND_SYNC -> {
                settingsRecyclerViewParent.visibility = View.GONE
                featureTitle.text = getString(R.string.settings_background_sync)
                enableFeatureSwitch.isChecked = legacyUser.isBackgroundSync

                enableFeatureSwitch.setOnCheckedChangeListener { view, isChecked ->

                    if (view.isPressed && isChecked != legacyUser.isBackgroundSync) {
                        legacyUser.isBackgroundSync = isChecked
                        if (legacyUser.isBackgroundSync) {
                            val alarmReceiver = AlarmReceiver()
                            alarmReceiver.setAlarm(ProtonMailApplication.getApplication())

                        }
                        legacyUser.save()
                    }
                }

                descriptionParent.visibility = View.VISIBLE
                description.text = getString(R.string.background_sync_subtitle)
                actionBarTitle = R.string.settings_background_sync
            }
            SettingsItem.SWIPE -> {
                val mailSettings = checkNotNull(userManager.getCurrentUserMailSettingsBlocking())
                setValue(
                    SettingsEnum.SWIPE_LEFT,
                    getString(SwipeAction.values()[mailSettings.leftSwipeAction].actionDescription)
                )
                setValue(
                    SettingsEnum.SWIPE_RIGHT,
                    getString(SwipeAction.values()[mailSettings.rightSwipeAction].actionDescription)
                )
                actionBarTitle = R.string.swiping_gesture
            }
            SettingsItem.LABELS_AND_FOLDERS -> {
                actionBarTitle = R.string.labels_and_folders
            }
            SettingsItem.PUSH_NOTIFICATIONS -> {
                setValue(SettingsEnum.EXTENDED_NOTIFICATION, getString(R.string.extended_notifications_description))
                setEnabled(SettingsEnum.EXTENDED_NOTIFICATION, legacyUser.isNotificationVisibilityLockScreen)

                setToggleListener(SettingsEnum.EXTENDED_NOTIFICATION) { _: View, isChecked: Boolean ->
                    if (isChecked != legacyUser.isNotificationVisibilityLockScreen) {
                        legacyUser.isNotificationVisibilityLockScreen = isChecked
                        legacyUser.save()
                    }
                }

                mNotificationOptionValue = legacyUser.notificationSetting
                val notificationOption =
                    resources.getStringArray(R.array.notification_options)[mNotificationOptionValue]
                setValue(SettingsEnum.NOTIFICATION_SETTINGS, notificationOption)

                actionBarTitle = R.string.push_notifications
            }
            SettingsItem.COMBINED_CONTACTS -> {
                setValue(
                    SettingsEnum.COMBINED_CONTACTS,
                    getString(R.string.turn_combined_contacts_on)
                )
                setEnabled(SettingsEnum.COMBINED_CONTACTS, legacyUser.combinedContacts)

                setToggleListener(SettingsEnum.COMBINED_CONTACTS) { _: View, isChecked: Boolean ->
                    if (isChecked != legacyUser.combinedContacts) {
                        legacyUser.combinedContacts = isChecked
                        legacyUser.save()
                    }
                }

                actionBarTitle = R.string.combined_contacts
            }
            SettingsItem.CONNECTIONS_VIA_THIRD_PARTIES -> {
                setValue(
                    SettingsEnum.ALLOW_SECURE_CONNECTIONS_VIA_THIRD_PARTIES,
                    getString(R.string.allow_secure_connections_via_third_parties_settings_description)
                )
                setEnabled(
                    SettingsEnum.ALLOW_SECURE_CONNECTIONS_VIA_THIRD_PARTIES,
                    legacyUser.allowSecureConnectionsViaThirdParties
                )

                setToggleListener(SettingsEnum.ALLOW_SECURE_CONNECTIONS_VIA_THIRD_PARTIES) { _, isChecked ->
                    legacyUser.allowSecureConnectionsViaThirdParties = isChecked

                    if (!isChecked) {
                        mNetworkUtil.networkConfigurator.networkSwitcher.reconfigureProxy(null)
                        legacyUser.usingDefaultApi = true
                    }
                }

                actionBarTitle = R.string.connections_via_third_parties
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            saveLastInteraction()
            saveAndFinish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        saveLastInteraction()
        saveAndFinish()
    }

    private fun View.getAllViews(): List<View> {
        if (this !is ViewGroup || childCount == 0) return listOf(this)

        return children
            .toList()
            .flatMap { it.getAllViews() }
            .plus(this as View)
    }

    private fun saveAndFinish() {
        val intent = Intent()

        for (child in settingsRecyclerView.getAllViews()) {
            child.clearFocus()
        }

        intent.putExtra(EXTRA_SETTINGS_ITEM_TYPE, settingsItemType)
        if (settingsItemType == SettingsItem.RECOVERY_EMAIL) {
            intent.putExtra(EXTRA_SETTINGS_ITEM_VALUE, recoveryEmailValue)
        }

        setResult(Activity.RESULT_OK, intent)
        saveLastInteraction()
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            renderViews()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @OnClick(R.id.save_new_email)
    fun onSaveNewRecoveryClicked() {
        if (isValidNewConfirmEmail) {
            showEmailChangeDialog(false)
        } else {
            showToast(R.string.recovery_emails_invalid, Toast.LENGTH_SHORT)
        }
    }

    @Subscribe
    fun onSettingsChangedEvent(event: SettingsChangedEvent) {
        progressBar.visibility = View.GONE
        if (event.status == AuthStatus.SUCCESS) {
            val user = mUserManager.user
            if (settingsItemType == SettingsItem.RECOVERY_EMAIL) {
                settingsItemValue = recoveryEmailValue
                if (recoveryEmailValue.isNullOrEmpty()) {
                    mUserManager.userSettings!!.notificationEmail = resources.getString(R.string.not_set)
                } else {
                    mUserManager.userSettings!!.notificationEmail = recoveryEmailValue
                }
                user.save()
            }
            if (!event.isBackPressed) {
                enableRecoveryEmailInput()
            } else {
                val intent = Intent()
                    .putExtra(EXTRA_SETTINGS_ITEM_TYPE, settingsItemType)
                    .putExtra(EXTRA_SETTINGS_ITEM_VALUE, recoveryEmailValue)
                setResult(RESULT_OK, intent)
                saveLastInteraction()
                finish()
            }
        } else {
            recoveryEmailValue = settingsItemValue
            when (event.status) {
                AuthStatus.INVALID_SERVER_PROOF -> {
                    showToast(R.string.invalid_server_proof, Toast.LENGTH_SHORT)
                }
                AuthStatus.FAILED -> {
                    showToast(event.error)
                }
                else -> {
                    showToast(event.error)
                }
            }
        }
    }

    private fun disableRecoveryEmailInput() {
        newRecoveryEmail.isFocusable = false
        newRecoveryEmail.isFocusableInTouchMode = false
    }

    private fun enableRecoveryEmailInput() {
        newRecoveryEmail.isFocusable = true
        newRecoveryEmail.isFocusableInTouchMode = true
        currentRecoveryEmail.setText(recoveryEmailValue)
        newRecoveryEmail.setText("")
        newRecoveryEmailConfirm.setText("")
    }

    private fun showEmailChangeDialog(backPressed: Boolean) {
        val hasTwoFactor = mUserManager.userSettings!!.twoFactor == 1
        val builder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.layout_password_confirm, null)
        val password = dialogView.findViewById<EditText>(R.id.current_password)
        val twoFactorCode = dialogView.findViewById<EditText>(R.id.two_factor_code)
        if (hasTwoFactor) {
            twoFactorCode.visibility = View.VISIBLE
        }
        builder.setView(dialogView)
        builder.setPositiveButton(R.string.okay) { dialog, _ ->
            val passString = password.text.toString()
            var twoFactorString = ""
            if (hasTwoFactor) {
                twoFactorString = twoFactorCode.text.toString()
            }
            if (passString.isEmpty() || twoFactorString.isEmpty() && hasTwoFactor) {
                showToast(R.string.password_not_valid, Toast.LENGTH_SHORT)
                newRecoveryEmail.setText("")
                newRecoveryEmailConfirm.setText("")
                dialog.cancel()
            } else {
                progressBar.visibility = View.VISIBLE
                recoveryEmailValue = newRecoveryEmail.text.toString()
                val job = UpdateSettingsJob(
                    notificationEmailChanged = true,
                    newEmail = recoveryEmailValue!!,
                    backPressed = backPressed,
                    password = passString.toByteArray() /*TODO passphrase*/,
                    twoFactor = twoFactorString
                )
                mJobManager.addJobInBackground(job)
                dialog.dismiss()
                disableRecoveryEmailInput()
            }
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
            if (backPressed) {
                saveLastInteraction()
                finish()
            }
        }
        val alert = builder.create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.iron_gray))
            val positiveButton = alert.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.setTextColor(ContextCompat.getColor(this, R.color.new_purple_dark))
            positiveButton.text = getString(R.string.enter)
        }
        alert.setCanceledOnTouchOutside(false)
        alert.show()
    }

    @Subscribe
    fun onLogoutEvent(event: LogoutEvent?) {
        moveToLogin()
    }
}
