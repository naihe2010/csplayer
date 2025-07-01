package naihe2010.csplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout

class SettingFragment : Fragment() {

    private lateinit var playerConfig: PlayerConfig


    private lateinit var tvPlaybackRateValue: TextView
    private lateinit var sliderPlaybackRate: Slider
    private lateinit var rgPlaybackOrder: RadioGroup
    private lateinit var rbSequential: View
    private lateinit var rbRandom: View
    private lateinit var rbLoop: View

    private lateinit var llLoopType: View
    private lateinit var rgLoopType: RadioGroup
    private lateinit var rbLoopFile: View
    private lateinit var rbLoopTime: View
    private lateinit var rbLoopSegment: View
    private lateinit var tilLoopInterval: TextInputLayout
    private lateinit var etLoopInterval: EditText
    private lateinit var tilSilenceThreshold: TextInputLayout
    private lateinit var etSilenceThreshold: EditText

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
        rbLoop = view.findViewById(R.id.rbLoop)

        llLoopType = view.findViewById(R.id.llLoopType)
        rgLoopType = view.findViewById(R.id.rgLoopType)
        rbLoopFile = view.findViewById(R.id.rbLoopFile)
        rbLoopTime = view.findViewById(R.id.rbLoopTime)
        rbLoopSegment = view.findViewById(R.id.rbLoopSegment)
        tilLoopInterval = view.findViewById(R.id.tilLoopInterval)
        etLoopInterval = view.findViewById(R.id.etLoopInterval)
        tilSilenceThreshold = view.findViewById(R.id.tilSilenceThreshold)
        etSilenceThreshold = view.findViewById(R.id.etSilenceThreshold)
    }

    private fun setupListeners() {


        sliderPlaybackRate.addOnChangeListener { _, value, _ ->
            tvPlaybackRateValue.text = String.format("%.1fx", value)
            playerConfig = playerConfig.updatePlaybackRate(value)
            playerConfig.save(requireContext())
            val intent = Intent(ACTION_PLAYBACK_RATE_CHANGED)
            intent.putExtra(EXTRA_PLAYBACK_RATE, value)
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
        }

        rgPlaybackOrder.setOnCheckedChangeListener { _, checkedId ->
            val newPlaybackOrder = when (checkedId) {
                R.id.rbSequential -> PlaybackOrder.SEQUENTIAL
                R.id.rbRandom -> PlaybackOrder.RANDOM
                R.id.rbLoop -> PlaybackOrder.LOOP
                else -> playerConfig.playbackOrder
            }
            playerConfig = playerConfig.updatePlaybackOrder(newPlaybackOrder)
            playerConfig.save(requireContext())

            llLoopType.visibility =
                if (newPlaybackOrder == PlaybackOrder.LOOP) View.VISIBLE else View.GONE
            tilLoopInterval.visibility =
                if (newPlaybackOrder == PlaybackOrder.LOOP && rgLoopType.checkedRadioButtonId == R.id.rbLoopTime) View.VISIBLE else View.GONE

            val intent = Intent(ACTION_PLAYBACK_ORDER_CHANGED)
            intent.putExtra(EXTRA_PLAYBACK_ORDER, newPlaybackOrder.name)
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
        }

        rgLoopType.setOnCheckedChangeListener { _, checkedId ->
            tilLoopInterval.visibility =
                if (checkedId == R.id.rbLoopTime) View.VISIBLE else View.GONE
            tilSilenceThreshold.visibility =
                if (checkedId == R.id.rbLoopSegment) View.VISIBLE else View.GONE

            playerConfig = playerConfig.updateLoopSettings(
                when (checkedId) {
                    R.id.rbLoopFile -> LoopType.FILE
                    R.id.rbLoopTime -> LoopType.TIME
                    R.id.rbLoopSegment -> LoopType.SEGMENT
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
                    playerConfig.loopType,
                    interval
                )
                playerConfig.save(requireContext())
            }
        }

        etSilenceThreshold.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val interval = etSilenceThreshold.text.toString().toIntOrNull() ?: 0
                playerConfig = playerConfig.updateSilenceThreshold(interval)
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
            PlaybackOrder.LOOP -> rgPlaybackOrder.check(R.id.rbLoop)
        }

        llLoopType.visibility =
            if (playerConfig.playbackOrder == PlaybackOrder.LOOP) View.VISIBLE else View.GONE

        when (playerConfig.loopType) {
            LoopType.FILE -> rgLoopType.check(R.id.rbLoopFile)
            LoopType.TIME -> rgLoopType.check(R.id.rbLoopTime)
            LoopType.SEGMENT -> rgLoopType.check(R.id.rbLoopSegment)
            else -> {}
        }

        tilLoopInterval.visibility =
            if (playerConfig.playbackOrder == PlaybackOrder.LOOP && playerConfig.loopType == LoopType.TIME) View.VISIBLE else View.GONE
        tilSilenceThreshold.visibility =
            if (playerConfig.playbackOrder == PlaybackOrder.LOOP && playerConfig.loopType == LoopType.SEGMENT) View.VISIBLE else View.GONE
        etLoopInterval.setText(playerConfig.loopInterval.toString())
        etSilenceThreshold.setText(playerConfig.silenceThreshold.toString())
    }

    companion object {
        const val ACTION_PLAYBACK_RATE_CHANGED = "naihe2010.csplayer.ACTION_PLAYBACK_RATE_CHANGED"
        const val EXTRA_PLAYBACK_RATE = "playback_rate"
        const val ACTION_PLAYBACK_ORDER_CHANGED = "naihe2010.csplayer.ACTION_PLAYBACK_ORDER_CHANGED"
        const val EXTRA_PLAYBACK_ORDER = "playback_order"
    }
}