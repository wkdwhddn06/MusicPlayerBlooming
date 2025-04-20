/*
 * Copyright (c) 2024 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.uniqtech.musicplayer.fragments.equalizer

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.uniqtech.musicplayer.R
import com.uniqtech.musicplayer.adapters.EQPresetAdapter
import com.uniqtech.musicplayer.databinding.DialogRecyclerViewBinding
import com.uniqtech.musicplayer.databinding.FragmentEqualizerBinding
import com.uniqtech.musicplayer.dialogs.InputDialog
import com.uniqtech.musicplayer.dialogs.MultiCheckDialog
import com.uniqtech.musicplayer.extensions.*
import com.uniqtech.musicplayer.fragments.base.AbsMainActivityFragment
import com.uniqtech.musicplayer.interfaces.IEQInterface
import com.uniqtech.musicplayer.interfaces.IEQPresetCallback
import com.uniqtech.musicplayer.model.EQPreset
import com.uniqtech.musicplayer.mvvm.ExportRequestResult
import com.uniqtech.musicplayer.mvvm.ImportRequestResult
import com.uniqtech.musicplayer.service.MusicPlayer
import com.uniqtech.musicplayer.service.equalizer.EqualizerManager
import com.uniqtech.musicplayer.service.equalizer.OpenSLESConstants
import com.uniqtech.musicplayer.util.Preferences
import com.uniqtech.musicplayer.views.AnimSlider
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Formatter
import java.util.Locale

/**
 * @author Christians M. A. (mardous)
 */
class EqualizerFragment : AbsMainActivityFragment(R.layout.fragment_equalizer),
    CompoundButton.OnCheckedChangeListener, Slider.OnSliderTouchListener, IEQInterface, IEQPresetCallback {

    private var _binding: FragmentEqualizerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EqualizerViewModel by viewModel()
    private val equalizerManager: EqualizerManager by inject()

    private lateinit var presetAdapter: EQPresetAdapter

    private lateinit var selectExportDocumentLauncher: ActivityResultLauncher<String>
    private lateinit var selectImportDocumentLauncher: ActivityResultLauncher<Array<String>>

    private var mPresetsDialog: Dialog? = null
    private var mReverbSpinnerAdapter: ArrayAdapter<String>? = null

    private var mEqualizerBands = 0
    private val mEqualizerSeekBar = arrayOfNulls<AnimSlider>(EqualizerManager.EQUALIZER_MAX_BANDS)

    private val formatBuilder = StringBuilder()
    private val formatter = Formatter(formatBuilder, Locale.getDefault())

    private val bandLevelRange: IntArray
        get() = equalizerManager.bandLevelRange

    private val centerFrequencies: IntArray
        get() = equalizerManager.centerFreqs

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView) {
            binding.equalizerBands.presetSwitch -> {
                viewModel.setEqualizerState(isChecked)
            }

            binding.equalizerEffects.loudnessEnhancerSwitch -> {
                if (isChecked) {
                    if (!Preferences.loudnessEnhancerWarningShown) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.warning_title)
                            .setMessage(R.string.loudness_enhancer_warning)
                            .setPositiveButton(android.R.string.ok, null)
                            .show {
                                Preferences.loudnessEnhancerWarningShown = true
                            }
                    }
                }
                if (equalizerManager.isLoudnessEnabled != isChecked) {
                    equalizerManager.isLoudnessEnabled = isChecked
                    MusicPlayer.updateEqualizer()
                }
                binding.equalizerEffects.loudnessGain.isEnabled = isChecked
            }

            binding.equalizerEffects.reverbSwitch -> {
                if (equalizerManager.isPresetReverbEnabled != isChecked) {
                    equalizerManager.isPresetReverbEnabled = isChecked
                    MusicPlayer.updateEqualizer()
                }
                binding.equalizerEffects.reverb.isEnabled = isChecked
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEqualizerBinding.bind(view).apply {
            with(appBarLayout.toolbar) {
                isTitleCentered = false
                setNavigationIcon(R.drawable.ic_back_24dp)
            }
        }.also { viewBinding ->
            viewBinding.equalizerBands.presetSwitch.isChecked =
                equalizerManager.isEqualizerSupported && equalizerManager.isEqualizerEnabled
            viewBinding.equalizerBands.presetSwitch.isEnabled = equalizerManager.isEqualizerSupported
            viewBinding.equalizerBands.presetSwitch.setOnCheckedChangeListener(this)
        }

        presetAdapter = EQPresetAdapter(requireContext(), equalizerManager.getEqualizerPresetsWithCustom(), this)

        selectExportDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE_APPLICATION)) { data: Uri? ->
                viewModel.exportConfiguration(data).observe(viewLifecycleOwner) { result ->
                    if (result.success && result.data != null && result.mimeType != null) {
                        Snackbar.make(binding.root, result.messageRes, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_share) {
                                val builder = ShareCompat.IntentBuilder(requireContext())
                                    .setType(result.mimeType)
                                    .setStream(result.data)
                                    .setChooserTitle(R.string.share_eq_configuration)
                                try {
                                    builder.startChooser()
                                } catch (_: ActivityNotFoundException) {
                                }
                            }
                            .show()
                    } else {
                        showToast(result.messageRes)
                    }
                }
            }
        selectImportDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { data: Uri? ->
            viewModel.requestImport(data).observe(viewLifecycleOwner) { requestResult ->
                if (requestResult.success) {
                    showImportDialog(requestResult)
                } else {
                    showToast(requestResult.messageRes)
                }
            }
        }

        view.applyScrollableContentInsets(binding.mainEqContainer)
        materialSharedAxis(view)
        setSupportActionBar(binding.appBarLayout.toolbar, getString(R.string.equalizer_label))

        setUpPresets()
        setUpEQViews()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_equalizer, menu)

        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        if (requireContext().packageManager.resolveActivity(intent) == null) {
            val openDSPItem = menu.findItem(R.id.action_open_dsp)
            if (openDSPItem != null) {
                openDSPItem.isVisible = false
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }

            R.id.action_open_dsp -> {
                val sessionId = MusicPlayer.audioSessionId
                if (sessionId != AudioEffect.ERROR_BAD_VALUE) {
                    try {
                        val equalizer = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                        equalizer.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                        equalizer.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        startActivityForResult(equalizer, DISPLAY_AUDIO_EFFECT_CONTROL_PANEL_REQUEST)
                    } catch (ex: ActivityNotFoundException) {
                        showToast(R.string.no_equalizer)
                    }
                } else {
                    showToast(R.string.no_audio_ID)
                }
                true
            }

            R.id.action_share_configuration -> {
                viewModel.requestExport().observe(viewLifecycleOwner) { requestResult ->
                    if (requestResult.success) {
                        showExportDialog(
                            R.string.share_configuration,
                            R.string.select_configurations_to_share,
                            requestResult
                        ) {
                            viewModel.sharePresets(requireContext(), it).observe(viewLifecycleOwner) { exportResult ->
                                if (exportResult.success) {
                                    val builder = ShareCompat.IntentBuilder(requireContext())
                                        .setChooserTitle(R.string.share_eq_configuration)
                                        .setStream(exportResult.data)
                                        .setType(exportResult.mimeType)
                                    try {
                                        builder.startChooser()
                                    } catch (_: ActivityNotFoundException) {
                                    }
                                } else {
                                    showToast(exportResult.messageRes)
                                }
                            }
                        }
                    } else {
                        showToast(R.string.there_are_no_saved_configurations)
                    }
                }
                true
            }

            R.id.action_export_configuration -> {
                viewModel.requestExport().observe(viewLifecycleOwner) { requestResult ->
                    if (requestResult.success) {
                        showExportDialog(
                            R.string.export_configuration,
                            R.string.select_configurations_to_export,
                            requestResult
                        ) { eqPresets ->
                            viewModel.generateExportData(eqPresets).observe(viewLifecycleOwner) { presetName ->
                                try {
                                    selectExportDocumentLauncher.launch(presetName)
                                    showToast(R.string.select_a_file_to_save_exported_configurations)
                                } catch (ignored: ActivityNotFoundException) {
                                }
                            }
                        }
                    } else {
                        showToast(requestResult.messageRes)
                    }
                }
                true
            }

            R.id.action_import_configuration -> {
                try {
                    selectImportDocumentLauncher.launch(arrayOf(MIME_TYPE_APPLICATION))
                    showToast(R.string.select_a_file_containing_booming_eq_presets)
                } catch (_: ActivityNotFoundException) {
                }
                true
            }

            R.id.action_reset_equalizer -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset_equalizer)
                    .setMessage(R.string.are_you_sure_you_want_to_reset_the_equalizer)
                    .setPositiveButton(R.string.yes) { _: DialogInterface, _: Int ->
                        viewModel.resetEqualizer()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }

            else -> false
        }
    }

    private fun setUpPresets() {
        if (equalizerManager.isEqualizerSupported) {
            // setup equalizer presets
            binding.equalizerBands.selectPreset.setOnClickListener {
                if (mPresetsDialog == null) {
                    val binding = DialogRecyclerViewBinding.inflate(layoutInflater).apply {
                        recyclerView.layoutManager = LinearLayoutManager(requireContext())
                        recyclerView.adapter = presetAdapter
                    }
                    mPresetsDialog = MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.select_preset)
                        .setView(binding.root)
                        .setPositiveButton(R.string.action_cancel, null)
                        .create()
                }
                mPresetsDialog?.show()
            }
            binding.equalizerBands.savePreset.setOnClickListener {
                InputDialog.Builder(requireContext())
                    .title(R.string.save_preset)
                    .message(R.string.please_enter_a_name_for_this_preset)
                    .hint(R.string.preset_name)
                    .maxLength(PRESET_NAME_MAX_LENGTH)
                    .checkablePrompt(R.string.replace_preset_with_same_name)
                    .positiveText(R.string.action_save)
                    .createDialog { dialog, inputLayout, text, checked ->
                        if (text.isNullOrBlank()) {
                            inputLayout.error = getString(R.string.preset_name_is_empty)
                        } else {
                            viewModel.savePreset(text, checked).observe(viewLifecycleOwner) {
                                showToast(it.messageRes)
                                if (it.canDismiss) {
                                    dialog.dismiss()
                                }
                            }
                        }
                        false
                    }
                    .show(childFragmentManager, "SAVE_PRESET")
            }
        } else {
            binding.equalizerBands.preset.text = getString(R.string.not_supported)
            binding.equalizerBands.selectPreset.isEnabled = false
            binding.equalizerBands.savePreset.isEnabled = false
        }
    }

    private fun setUpEQViews() {
        setUpEqualizerViews()
        setUpBassBoostViews()
        setUpVirtualizerViews()
        setUpLoudnessViews()
        setUpReverbViews()
    }

    private fun setUpEqualizerViews() {
        //Initialize the equalizer elements
        mEqualizerBands = equalizerManager.numberOfBands

        val centerFreqs = centerFrequencies
        val bandLevelRange = bandLevelRange
        val maxProgress = bandLevelRange[1] / 100 - bandLevelRange[0] / 100

        for (band in 0 until mEqualizerBands) {
            //Unit conversion from mHz to Hz and use k prefix if necessary to display
            var centerFreqHz = centerFreqs[band] / 1000.toFloat()
            var unit = "Hz"
            if (centerFreqHz >= 1000) {
                centerFreqHz /= 1000
                unit = "KHz"
            }

            binding.equalizerBands.eqContainer.findViewById<View>(eqViewElementIds[band][0]).visibility = View.VISIBLE
            binding.equalizerBands.eqContainer.findViewById<View>(eqViewTextElementIds[band][0]).visibility =
                View.VISIBLE
            binding.equalizerBands.eqContainer.findViewById<View>(eqViewElementIds[band][1]).visibility = View.VISIBLE
            binding.equalizerBands.eqContainer.findViewById<View>(eqViewTextElementIds[band][1]).visibility =
                View.VISIBLE
            (binding.equalizerBands.eqContainer.findViewById<View>(eqViewElementIds[band][0]) as TextView).text =
                String.format("%s %s", freqFormat(centerFreqHz), unit)

            mEqualizerSeekBar[band] =
                binding.equalizerBands.eqContainer.findViewById<AnimSlider>(eqViewElementIds[band][1]).apply {
                    valueTo = maxProgress.toFloat()

                    setLabelFormatter {
                        String.format(Locale.ROOT, "%+.1fdb", it - maxProgress / 2)
                    }

                    addOnSliderTouchListener(this@EqualizerFragment)
                    addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                        override fun onStartTrackingTouch(slider: Slider) {}
                        override fun onStopTrackingTouch(slider: Slider) {
                            viewModel.setCustomPresetBandLevel(band, bandLevelRange[0] + slider.value.toInt() * 100)
                        }
                    })
                }
        }
    }

    private fun setUpBassBoostViews() {
        if (equalizerManager.isBassBoostSupported) {
            binding.equalizerEffects.bassboostStrength.apply {
                valueTo =
                    (OpenSLESConstants.BASSBOOST_MAX_STRENGTH - OpenSLESConstants.BASSBOOST_MIN_STRENGTH).toFloat()

                addOnSliderTouchListener(this@EqualizerFragment)
                addOnChangeListener { slider, value, fromUser ->
                    if (fromUser) {
                        setSoundEffectDisplay(
                            binding.equalizerEffects.bassboostStrengthDisplay,
                            value.toInt(),
                            slider.valueTo.toInt()
                        )
                        viewModel.setBassStrength(value)
                    }
                }
            }
        } else {
            binding.equalizerEffects.bassboostStrength.isEnabled = false
        }
    }

    private fun setUpVirtualizerViews() {
        if (equalizerManager.isVirtualizerSupported) {
            binding.equalizerEffects.virtualizerStrength.apply {
                valueTo =
                    (OpenSLESConstants.VIRTUALIZER_MAX_STRENGTH - OpenSLESConstants.VIRTUALIZER_MIN_STRENGTH).toFloat()

                addOnSliderTouchListener(this@EqualizerFragment)
                addOnChangeListener { slider, value, fromUser ->
                    if (fromUser) {
                        setSoundEffectDisplay(
                            binding.equalizerEffects.virtualizerStrengthDisplay,
                            value.toInt(),
                            slider.valueTo.toInt()
                        )
                        viewModel.setVirtualizerStrength(value)
                    }
                }
            }
        } else {
            binding.equalizerEffects.virtualizerStrength.isEnabled = false
        }
    }

    private fun setUpLoudnessViews() {
        if (equalizerManager.isLoudnessEnhancerSupported) {
            binding.equalizerEffects.loudnessEnhancerSwitch.apply {
                isChecked = equalizerManager.isLoudnessEnabled
                setOnCheckedChangeListener(this@EqualizerFragment)
            }

            binding.equalizerEffects.loudnessGain.apply {
                isEnabled = equalizerManager.isEqualizerEnabled && equalizerManager.isLoudnessEnabled

                valueFrom = OpenSLESConstants.MINIMUM_LOUDNESS_GAIN.toFloat()
                valueTo = OpenSLESConstants.MAXIMUM_LOUDNESS_GAIN.toFloat()

                setLoudnessGainDisplay(equalizerManager.loudnessGain.also { loudnessGain ->
                    value = loudnessGain.toFloat()
                })

                addOnChangeListener { _, value, fromUser ->
                    setLoudnessGainDisplay(value.toInt())
                    if (fromUser) {
                        equalizerManager.loudnessGain = value.toInt()
                        MusicPlayer.updateEqualizer()
                    }
                }
            }
        } else {
            binding.equalizerEffects.loudnessEnhancerSwitch.isEnabled = false
            binding.equalizerEffects.loudnessGain.isEnabled = false
        }
    }

    private fun setSoundEffectDisplay(view: TextView, value: Int, maxValue: Int) {
        view.text = String.format(Locale.ROOT, "%d%%", (value * 100) / maxValue)
    }

    private fun setLoudnessGainDisplay(loudnessGain: Int) {
        binding.equalizerEffects.loudnessGainDisplay.text = String.format(Locale.ROOT, "%d mDb", loudnessGain)
    }

    private fun setUpReverbViews() {
        if (equalizerManager.isPresetReverbSupported) {
            binding.equalizerEffects.reverbSwitch.apply {
                isChecked = equalizerManager.isPresetReverbEnabled
                setOnCheckedChangeListener(this@EqualizerFragment)
            }

            if (mReverbSpinnerAdapter == null || mReverbSpinnerAdapter!!.count == 0) {
                mReverbSpinnerAdapter = ArrayAdapter(
                    requireContext(), android.R.layout.simple_spinner_item,
                    resources.getStringArray(R.array.reverb_preset_names)
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }.also {
                    binding.equalizerEffects.reverb.adapter = it
                    binding.equalizerEffects.reverb.setSelection(equalizerManager.presetReverbPreset)
                    binding.equalizerEffects.reverb.onItemSelectedListener =
                        object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                equalizerManager.presetReverbPreset = position
                                MusicPlayer.updateEqualizer()
                            }

                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                }
            }
        } else {
            mReverbSpinnerAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item, arrayOf(getString(R.string.not_supported))
            ).also {
                binding.equalizerEffects.reverb.isEnabled = false
                binding.equalizerEffects.reverb.adapter = it
                binding.equalizerEffects.reverb.setSelection(0)
            }

            binding.equalizerEffects.reverbSwitch.isEnabled = false
        }
    }

    override fun onStartTrackingTouch(slider: Slider) {}

    override fun onStopTrackingTouch(slider: Slider) {
        // we must force the "Custom" entry to be updated
        equalizerManager.requestPresetsList()
        MusicPlayer.updateEqualizer()
    }

    override fun eqStateChanged(isGlobalEnabled: Boolean, isBeingReset: Boolean) {
        runOnUi {
            if (binding.equalizerBands.presetSwitch.isChecked != isGlobalEnabled) {
                binding.equalizerBands.presetSwitch.isChecked = isGlobalEnabled
            }

            for (seekBar in mEqualizerSeekBar) {
                seekBar?.isEnabled = isGlobalEnabled
            }

            binding.equalizerBands.selectPreset.isEnabled = isGlobalEnabled
            binding.equalizerBands.savePreset.isEnabled =
                isGlobalEnabled && viewModel.isCustomPresetSelected()

            // Toggle sound effects
            binding.equalizerEffects.virtualizerStrength.isEnabled =
                isGlobalEnabled && equalizerManager.isVirtualizerSupported
            binding.equalizerEffects.bassboostStrength.isEnabled =
                isGlobalEnabled && equalizerManager.isBassBoostSupported

            // Toggle preset reverb
            binding.equalizerEffects.reverbSwitch.isEnabled =
                isGlobalEnabled && equalizerManager.isPresetReverbSupported
            binding.equalizerEffects.reverb.isEnabled =
                isGlobalEnabled && equalizerManager.isPresetReverbEnabled

            // Toggle loudness enhancer
            binding.equalizerEffects.loudnessEnhancerSwitch.isEnabled =
                isGlobalEnabled && equalizerManager.isLoudnessEnhancerSupported
            binding.equalizerEffects.loudnessGain.isEnabled =
                isGlobalEnabled && equalizerManager.isLoudnessEnabled

            if (isBeingReset) {
                binding.equalizerEffects.reverb.setSelection(equalizerManager.presetReverbPreset)
                binding.equalizerEffects.loudnessGain.value = equalizerManager.loudnessGain.toFloat()
            }
        }
    }

    override fun eqPresetListChanged(presets: List<EQPreset>) {
        runOnUi {
            presetAdapter.presets = equalizerManager.getEqualizerPresetsWithCustom(presets)
        }
    }

    override fun eqPresetChanged(oldPreset: EQPreset?, newPreset: EQPreset?) {
        if (!equalizerManager.isEqualizerSupported)
            return

        runOnUi {
            if (newPreset == null) {
                binding.equalizerBands.preset.text = getString(R.string.no_preset)
                binding.equalizerBands.savePreset.isEnabled = false

                for (band in 0 until mEqualizerBands) {
                    mEqualizerSeekBar[band]?.setValueAnimated(bandLevelRange[1] / 100.0f)
                }

                binding.equalizerEffects.virtualizerStrength.setValueAnimated(0f)
                binding.equalizerEffects.bassboostStrength.setValueAnimated(0f)
            } else {
                binding.equalizerBands.preset.text = newPreset.getName(requireContext())
                binding.equalizerBands.savePreset.isEnabled = newPreset.isCustom

                if (!newPreset.areSameLevels(oldPreset)) {
                    val levels = newPreset.levels
                    for (band in levels.indices) {
                        mEqualizerSeekBar[band]?.setValueAnimated(
                            levels[band].toFloat().let { floatLevel ->
                                bandLevelRange[1] / 100.0f + floatLevel / 100.0f
                            })
                    }
                }

                binding.equalizerEffects.virtualizerStrength.setValueAnimated(
                    newPreset.getEffect(EqualizerManager.EFFECT_TYPE_VIRTUALIZER)
                        .also { value ->
                            setSoundEffectDisplay(
                                binding.equalizerEffects.virtualizerStrengthDisplay,
                                value.toInt(),
                                binding.equalizerEffects.virtualizerStrength.valueTo.toInt()
                            )
                        })
                binding.equalizerEffects.bassboostStrength.setValueAnimated(
                    newPreset.getEffect(EqualizerManager.EFFECT_TYPE_BASS_BOOST)
                        .also { value ->
                            setSoundEffectDisplay(
                                binding.equalizerEffects.bassboostStrengthDisplay,
                                value.toInt(),
                                binding.equalizerEffects.bassboostStrength.valueTo.toInt()
                            )
                        })
            }
        }
    }

    override fun eqPresetSelected(eqPreset: EQPreset) {
        viewModel.setEqualizerPreset(eqPreset)
        mPresetsDialog?.dismiss()
    }

    override fun editEQPreset(eqPreset: EQPreset) {
        InputDialog.Builder(requireContext())
            .title(R.string.rename_preset)
            .message(R.string.please_enter_a_new_name_for_this_preset)
            .hint(R.string.preset_name)
            .prefill(eqPreset.getName(requireContext()))
            .positiveText(R.string.rename_action)
            .createDialog { dialog, inputLayout, text, _ ->
                if (text.isNullOrBlank()) {
                    inputLayout.error = getString(R.string.preset_name_is_empty)
                } else {
                    viewModel.renamePreset(eqPreset, text).observe(viewLifecycleOwner) {
                        showToast(it.messageRes)
                        if (it.canDismiss) {
                            dialog.dismiss()
                        }
                    }
                }
                false
            }
            .show(childFragmentManager, "RENAME_PRESET")
    }

    override fun deleteEQPreset(eqPreset: EQPreset) {
        val presetName = eqPreset.getName(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_preset)
            .setMessage(getString(R.string.delete_preset_x, presetName).toHtml())
            .setPositiveButton(R.string.yes) { _: DialogInterface, _: Int ->
                viewModel.deletePreset(eqPreset).observe(viewLifecycleOwner) { result ->
                    if (result.success) {
                        showToast(getString(R.string.preset_x_deleted, presetName))
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showImportDialog(requestResult: ImportRequestResult) {
        MultiCheckDialog.Builder(requireContext())
            .title(R.string.import_configuration)
            .message(R.string.select_configurations_to_import)
            .items(requestResult.presetNames)
            .createDialog { _, whichPos, _ ->
                val toImport = requestResult.presets.filterIndexed { index, _ ->
                    whichPos.contains(index)
                }
                viewModel.importPresets(toImport).observe(viewLifecycleOwner) { importResult ->
                    if (importResult.success && importResult.imported > 0) {
                        showToast(getString(R.string.imported_x_presets, importResult.imported))
                    } else {
                        showToast(requestResult.messageRes)
                    }
                }
                true
            }
            .show(childFragmentManager, "IMPORT_PRESET_DIALOG")
    }

    private fun showExportDialog(
        titleRes: Int,
        messageRes: Int,
        requestResult: ExportRequestResult,
        selection: (List<EQPreset>) -> Unit
    ) {
        MultiCheckDialog.Builder(requireContext())
            .title(titleRes)
            .message(messageRes)
            .items(requestResult.presetNames)
            .createDialog { _, whichPos, _ ->
                selection(requestResult.presets.filterIndexed { index, _ ->
                    whichPos.contains(index)
                })
                true
            }
            .show(childFragmentManager, "EXPORT_PRESET_DIALOG")
    }

    override fun onResume() {
        super.onResume()
        viewModel.bindEqualizer(this)
    }

    override fun onPause() {
        super.onPause()
        viewModel.unbindEqualizer(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mEqualizerSeekBar.isNotEmpty()) {
            mEqualizerSeekBar.forEach { slider ->
                slider?.clearOnChangeListeners()
                slider?.clearOnSliderTouchListeners()
            }
        }
        binding.equalizerEffects.bassboostStrength.let { slider ->
            slider.clearOnChangeListeners()
            slider.clearOnSliderTouchListeners()
        }
        binding.equalizerEffects.virtualizerStrength.let { slider ->
            slider.clearOnChangeListeners()
            slider.clearOnSliderTouchListeners()
        }
        binding.equalizerEffects.loudnessGain.clearOnChangeListeners()
        _binding = null
    }

    private fun freqFormat(vararg args: Any): String {
        formatBuilder.setLength(0)
        formatter.format("%.0f", *args)
        return formatBuilder.toString()
    }

    companion object {

        private const val PRESET_NAME_MAX_LENGTH = 32
        private const val DISPLAY_AUDIO_EFFECT_CONTROL_PANEL_REQUEST = 1000

        /**
         * Mapping for the EQ widget ids per band
         */
        private val eqViewElementIds = arrayOf(
            intArrayOf(R.id.EqBand0TopTextView, R.id.EqBand0SeekBar),
            intArrayOf(R.id.EqBand1TopTextView, R.id.EqBand1SeekBar),
            intArrayOf(R.id.EqBand2TopTextView, R.id.EqBand2SeekBar),
            intArrayOf(R.id.EqBand3TopTextView, R.id.EqBand3SeekBar),
            intArrayOf(R.id.EqBand4TopTextView, R.id.EqBand4SeekBar),
            intArrayOf(R.id.EqBand5TopTextView, R.id.EqBand5SeekBar)
        )

        /**
         * Mapping for the EQ widget ids per band
         */
        private val eqViewTextElementIds = arrayOf(
            intArrayOf(R.id.EqBand0LeftTextView, R.id.EqBand0RightTextView),
            intArrayOf(R.id.EqBand1LeftTextView, R.id.EqBand1RightTextView),
            intArrayOf(R.id.EqBand2LeftTextView, R.id.EqBand2RightTextView),
            intArrayOf(R.id.EqBand3LeftTextView, R.id.EqBand3RightTextView),
            intArrayOf(R.id.EqBand4LeftTextView, R.id.EqBand4RightTextView),
            intArrayOf(R.id.EqBand5LeftTextView, R.id.EqBand5RightTextView)
        )
    }
}