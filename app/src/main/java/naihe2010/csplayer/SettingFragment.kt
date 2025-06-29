package naihe2010.csplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout

class SettingFragment : Fragment() {

    private lateinit var playerConfig: PlayerConfig


    private lateinit var tvPlaybackRateValue: TextView
    private lateinit var sliderPlaybackRate: Slider
    private lateinit var rgPlaybackOrder: RadioGroup
    private lateinit var rbSequential: View
    private lateinit var rbRandom: View
    private lateinit var rbShuffle: View
    private lateinit var switchLoopEnabled: SwitchMaterial
    private lateinit var llLoopType: View
    private lateinit var rgLoopType: RadioGroup
    private lateinit var rbLoopFile: View
    private lateinit var rbLoopTime: View
    private lateinit var tilLoopInterval: TextInputLayout
    private lateinit var etLoopInterval: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        playerConfig = PlayerConfig.getInstance(requireContext())

        findViews(view)
        setupListeners()
        loadSettings()

        return view
    }

    private fun findViews(view: View) {

        tvPlaybackRateValue = view.findViewById(R.id.tvPlaybackRateValue)
        sliderPlaybackRate = view.findViewById(R.id.sliderPlaybackRate)
        rgPlaybackOrder = view.findViewById(R.id.rgPlaybackOrder)
        rbSequential = view.findViewById(R.id.rbSequential)
        rbRandom = view.findViewById(R.id.rbRandom)
        rbShuffle = view.findViewById(R.id.rbShuffle)
        switchLoopEnabled = view.findViewById(R.id.switchLoopEnabled)
        llLoopType = view.findViewById(R.id.llLoopType)
        rgLoopType = view.findViewById(R.id.rgLoopType)
        rbLoopFile = view.findViewById(R.id.rbLoopFile)
        rbLoopTime = view.findViewById(R.id.rbLoopTime)
        tilLoopInterval = view.findViewById(R.id.tilLoopInterval)
        etLoopInterval = view.findViewById(R.id.etLoopInterval)
    }

    private fun setupListeners() {


        sliderPlaybackRate.addOnChangeListener { _, value, _ ->
            tvPlaybackRateValue.text = String.format("%.1fx", value)
            playerConfig = playerConfig.updatePlaybackRate(value)
            playerConfig.save(requireContext())
        }

        rgPlaybackOrder.setOnCheckedChangeListener { _, checkedId ->
            playerConfig = when (checkedId) {
                R.id.rbSequential -> playerConfig.updatePlaybackOrder(PlaybackOrder.SEQUENTIAL)
                R.id.rbRandom -> playerConfig.updatePlaybackOrder(PlaybackOrder.RANDOM)
                R.id.rbShuffle -> playerConfig.updatePlaybackOrder(PlaybackOrder.SHUFFLE)
                else -> playerConfig
            }
            playerConfig.save(requireContext())
        }

        switchLoopEnabled.setOnCheckedChangeListener { _, isChecked ->
            llLoopType.visibility = if (isChecked) View.VISIBLE else View.GONE
            tilLoopInterval.visibility =
                if (isChecked && rgLoopType.checkedRadioButtonId == R.id.rbLoopTime) View.VISIBLE else View.GONE
            playerConfig = playerConfig.updateLoopSettings(
                isChecked,
                playerConfig.loopType,
                playerConfig.loopInterval
            )
            playerConfig.save(requireContext())
        }

        rgLoopType.setOnCheckedChangeListener { _, checkedId ->
            tilLoopInterval.visibility =
                if (checkedId == R.id.rbLoopTime) View.VISIBLE else View.GONE
            playerConfig = playerConfig.updateLoopSettings(
                playerConfig.isLoopEnabled,
                when (checkedId) {
                    R.id.rbLoopFile -> LoopType.FILE
                    R.id.rbLoopTime -> LoopType.TIME
                    else -> playerConfig.loopType
                },
                playerConfig.loopInterval
            )
            playerConfig.save(requireContext())
        }

        etLoopInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val interval = etLoopInterval.text.toString().toIntOrNull() ?: 0
                playerConfig = playerConfig.updateLoopSettings(
                    playerConfig.isLoopEnabled,
                    playerConfig.loopType,
                    interval
                )
                playerConfig.save(requireContext())
            }
        }
    }

    private fun loadSettings() {
        tvPlaybackRateValue.text = String.format("%.1fx", playerConfig.playbackRate)
        sliderPlaybackRate.value = playerConfig.playbackRate

        when (playerConfig.playbackOrder) {
            PlaybackOrder.SEQUENTIAL -> rgPlaybackOrder.check(R.id.rbSequential)
            PlaybackOrder.RANDOM -> rgPlaybackOrder.check(R.id.rbRandom)
            PlaybackOrder.SHUFFLE -> rgPlaybackOrder.check(R.id.rbShuffle)
        }

        switchLoopEnabled.isChecked = playerConfig.isLoopEnabled
        llLoopType.visibility = if (playerConfig.isLoopEnabled) View.VISIBLE else View.GONE

        when (playerConfig.loopType) {
            LoopType.FILE -> rgLoopType.check(R.id.rbLoopFile)
            LoopType.TIME -> rgLoopType.check(R.id.rbLoopTime)
            else -> {}
        }

        tilLoopInterval.visibility =
            if (playerConfig.isLoopEnabled && playerConfig.loopType == LoopType.TIME) View.VISIBLE else View.GONE
        etLoopInterval.setText(playerConfig.loopInterval.toString())
    }


}