package com.opentdi.shared.csv

/** One session CSV (§12.2, §12.3): its file name in `filesDir/sessions/<sessionId>/` and its header. */
data class CsvFile(val name: String, val columns: List<String>)

/** Every session CSV. Column lists are the spec's text; a value note such as `late(0/1)` is not part of the name. */
object CsvFiles {
    val W_CONFIG = file("w_config.csv", "key,value")
    val W_ACCEL_RAW = file("w_accel_raw.csv", "sdk_ts_ms,x,y,z")
    val W_SENSOR_RAW = file(
        "w_sensor_raw.csv",
        "arrival_wall_ms,sdk_ts_ms,tracker(HR/EDA),hr_bpm,hr_status,ibi_ms(;),ibi_status(;),eda_us,eda_status," +
            "batch_size,late(0/1)",
    )
    val W_PACKETS = file(
        "w_packets.csv",
        "ts_ms,seq,source,hr_bpm,hr_status,ibi_ms(;),rmssd_ms,eda_us,eda_status,accel_mean_g,accel_rms_g," +
            "accel_peak_g,stillness,jerk,limp,tilt_deg,n_accel_samples,emitted_wall_ms,skin_temp_c,spo2_pct",
    )
    val W_ACCEL_EVENTS = file("w_accel_events.csv", "sample_ts_ms,event,value1,value2,detail")
    val W_ENGINE = file(
        "w_engine.csv",
        "ts_ms,state,confidence,detection_active,still_mean,moving_s,gate,hr_ma,hr_drop,hr_sig,rmssd_rise,rmssd_sig," +
            "eda_slope,eda_bonus,jerk_bonus,limp_bonus,hold_s,hold_req_s,refractory",
    )
    val W_INPUTS = file("w_inputs.csv", "after_packet_ts_ms,wall_ms,input,args")
    val W_TRANSITIONS = file("w_transitions.csv", "ts_ms,wall_ms,event,from,to,reason,cycle,completed_cycles,detail")
    val W_LINK = file("w_link.csv", "wall_ms,dir,path,type,session_seq_or_cycle,result,rtt_ms")
    val W_SKIN_TEMP = file(
        "w_skin_temp.csv",
        "arrival_wall_ms,sdk_ts_ms,object_temp_c,ambient_temp_c,status,batch_size",
    )
    val W_SPO2 = file(
        "w_spo2.csv",
        "arrival_wall_ms,sdk_ts_ms,spo2_pct,hr_bpm,status," +
            "event(DATA/SPO2_RESULT/SPO2_TIMEOUT/SPO2_ERROR/SPO2_ABORTED),hr_ok_points,window_ms",
    )
    val W_PPG_RAW = file("w_ppg_raw.csv", "sdk_ts_ms,ppg_green,green_status,ppg_ir,ir_status,ppg_red,red_status")

    val P_CONFIG = CsvFile("p_config.csv", W_CONFIG.columns)

    // "every w_packets.csv packet column" = the BiosignalPacket fields; seq, tilt_deg, n_accel_samples and
    // emitted_wall_ms never leave the watch (frame_seq carries the frame counter).
    val P_FRAMES = CsvFile(
        "p_frames.csv",
        listOf("rx_wall_ms", "batch_seq", "frame_seq") +
            (W_PACKETS.columns - setOf("seq", "tilt_deg", "n_accel_samples", "emitted_wall_ms")) +
            names(
                "state,state_reason,completed_cycles,confidence,gate,hr_sig,rmssd_sig,eda_bonus,jerk_bonus," +
                    "limp_bonus,hold_s,hold_req_s",
            ),
    )
    val P_TRANSITIONS = file("p_transitions.csv", "wall_ms,elapsed_ms,from,to,reason,cycle,driver(WATCH/PHONE/TIMED)")
    val P_AUDIO = file(
        "p_audio.csv",
        "wall_ms,elapsed_ms,cycle,event,cue,route,routed_type,gain,fade_ms,focus,dnd_filter,dnd_categories," +
            "stream_index,detail",
    )
    val P_LINK = CsvFile("p_link.csv", W_LINK.columns)

    private fun file(name: String, spec: String) = CsvFile(name, names(spec))

    private fun names(spec: String) = spec.split(",").map { it.substringBefore("(") }
}
