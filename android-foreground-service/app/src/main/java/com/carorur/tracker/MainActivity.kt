package com.carorur.tracker

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtLibraryStatus: TextView
    private lateinit var txtNowPlaying: TextView
    private lateinit var inputPlaylistName: EditText
    private lateinit var spinnerPlaylists: Spinner
    private lateinit var listDeviceTracks: ListView
    private lateinit var listPlaylistTracks: ListView

    private val deviceTracks = mutableListOf<LocalTrack>()
    private val playlists = linkedMapOf<String, MutableList<String>>()
    private lateinit var deviceTracksAdapter: ArrayAdapter<String>
    private lateinit var playlistTracksAdapter: ArrayAdapter<String>
    private lateinit var playlistNamesAdapter: ArrayAdapter<String>
    private var mediaPlayer: MediaPlayer? = null

    private val prefs by lazy {
        getSharedPreferences("carorur_native", MODE_PRIVATE)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                startTrackerService()
            } else {
                updateStatus("GPS: faltan permisos para iniciar el servicio")
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadDeviceAudio()
            } else {
                txtLibraryStatus.text = "Permiso de audio denegado. Sin ese permiso no se puede leer la musica del dispositivo."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtLibraryStatus = findViewById(R.id.txtLibraryStatus)
        txtNowPlaying = findViewById(R.id.txtNowPlaying)
        inputPlaylistName = findViewById(R.id.inputPlaylistName)
        spinnerPlaylists = findViewById(R.id.spinnerPlaylists)
        listDeviceTracks = findViewById(R.id.listDeviceTracks)
        listPlaylistTracks = findViewById(R.id.listPlaylistTracks)

        deviceTracksAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, mutableListOf())
        playlistTracksAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf())
        playlistNamesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mutableListOf())

        listDeviceTracks.adapter = deviceTracksAdapter
        listPlaylistTracks.adapter = playlistTracksAdapter
        spinnerPlaylists.adapter = playlistNamesAdapter

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestNeededPermissionsThenStart()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, LocationForegroundService::class.java))
            updateStatus("Estado: detenido")
        }

        findViewById<Button>(R.id.btnLoadMusic).setOnClickListener {
            requestAudioPermissionThenLoad()
        }

        findViewById<Button>(R.id.btnPlayChecked).setOnClickListener {
            playFirstCheckedTrack()
        }

        findViewById<Button>(R.id.btnSavePlaylist).setOnClickListener {
            saveCheckedTracksToPlaylist()
        }

        findViewById<Button>(R.id.btnPlayPlaylistTrack).setOnClickListener {
            playSelectedPlaylistTrack()
        }

        findViewById<Button>(R.id.btnStopAudio).setOnClickListener {
            stopAudioPlayback()
        }

        spinnerPlaylists.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshPlaylistTracks()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        loadSavedPlaylists()
        refreshPlaylistNames()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioPlayback(releaseOnly = true)
    }

    private fun requestNeededPermissionsThenStart() {
        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= 33) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= 29) {
            required.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }

        startTrackerService()
    }

    private fun requestAudioPermissionThenLoad() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadDeviceAudio()
            return
        }

        audioPermissionLauncher.launch(permission)
    }

    private fun startTrackerService() {
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        updateStatus("Estado: activo en foreground")
    }

    private fun loadDeviceAudio() {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val tracks = mutableListOf<LocalTrack>()
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn).orEmpty()
                val artist = cursor.getString(artistColumn).orEmpty()
                val durationMs = cursor.getLong(durationColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                tracks += LocalTrack(id, title, artist, durationMs, uri)
            }
        }

        deviceTracks.clear()
        deviceTracks.addAll(tracks)

        deviceTracksAdapter.clear()
        deviceTracksAdapter.addAll(deviceTracks.map { it.displayLabel() })
        deviceTracksAdapter.notifyDataSetChanged()

        for (index in 0 until listDeviceTracks.count) {
            listDeviceTracks.setItemChecked(index, false)
        }

        txtLibraryStatus.text = if (deviceTracks.isEmpty()) {
            "No se han encontrado audios locales en MediaStore."
        } else {
            "Se han cargado ${deviceTracks.size} canciones del dispositivo."
        }

        refreshPlaylistTracks()
    }

    private fun playFirstCheckedTrack() {
        val checked = collectCheckedTracks()
        if (checked.isEmpty()) {
            txtNowPlaying.text = "Reproductor: marca al menos una cancion de la biblioteca"
            return
        }
        playTrackUri(checked.first().uri, checked.first().displayLabel())
    }

    private fun saveCheckedTracksToPlaylist() {
        val selectedTracks = collectCheckedTracks()
        if (selectedTracks.isEmpty()) {
            txtLibraryStatus.text = "Marca una o varias canciones antes de guardar una playlist."
            return
        }

        val typedName = inputPlaylistName.text.toString().trim()
        val existingName = spinnerPlaylists.selectedItem?.toString().orEmpty().takeIf { it.isNotBlank() }
        val playlistName = when {
            typedName.isNotBlank() -> typedName
            existingName != null -> existingName
            else -> "Mi playlist"
        }

        val currentUris = playlists.getOrPut(playlistName) { mutableListOf() }
        val selectedUris = selectedTracks.map { it.uri.toString() }
        selectedUris.forEach { uri ->
            if (!currentUris.contains(uri)) {
                currentUris += uri
            }
        }

        savePlaylists()
        refreshPlaylistNames(playlistName)
        refreshPlaylistTracks()
        inputPlaylistName.setText(playlistName)
        txtLibraryStatus.text = "Playlist '$playlistName' guardada con ${currentUris.size} temas."
    }

    private fun playSelectedPlaylistTrack() {
        val playlistName = spinnerPlaylists.selectedItem?.toString().orEmpty()
        if (playlistName.isBlank()) {
            txtNowPlaying.text = "Reproductor: no hay playlist nativa seleccionada"
            return
        }

        val playlistUris = playlists[playlistName].orEmpty()
        val selectedPosition = listPlaylistTracks.checkedItemPosition
        if (selectedPosition == ListView.INVALID_POSITION || selectedPosition >= playlistUris.size) {
            txtNowPlaying.text = "Reproductor: elige un tema de la playlist"
            return
        }

        val uriString = playlistUris[selectedPosition]
        val track = deviceTracks.firstOrNull { it.uri.toString() == uriString }
        playTrackUri(Uri.parse(uriString), track?.displayLabel() ?: "Tema de playlist")
    }

    private fun playTrackUri(uri: Uri, label: String) {
        stopAudioPlayback(releaseOnly = true)
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener {
                    it.start()
                    txtNowPlaying.text = "Reproductor: $label"
                }
                setOnCompletionListener {
                    txtNowPlaying.text = "Reproductor: reproduccion finalizada"
                }
                prepareAsync()
            }
            txtNowPlaying.text = "Reproductor: cargando $label"
        } catch (_: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            txtNowPlaying.text = "Reproductor: no se ha podido abrir ese archivo de audio"
        }
    }

    private fun stopAudioPlayback(releaseOnly: Boolean = false) {
        mediaPlayer?.run {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        if (!releaseOnly) {
            txtNowPlaying.text = "Reproductor: parado"
        }
    }

    private fun collectCheckedTracks(): List<LocalTrack> {
        val checkedTracks = mutableListOf<LocalTrack>()
        for (index in deviceTracks.indices) {
            if (listDeviceTracks.isItemChecked(index)) {
                checkedTracks += deviceTracks[index]
            }
        }
        return checkedTracks
    }

    private fun loadSavedPlaylists() {
        playlists.clear()
        val rawJson = prefs.getString(KEY_PLAYLISTS_JSON, null) ?: return
        val root = JSONObject(rawJson)
        val keys = root.keys()
        while (keys.hasNext()) {
            val playlistName = keys.next()
            val array = root.optJSONArray(playlistName) ?: JSONArray()
            val items = mutableListOf<String>()
            for (i in 0 until array.length()) {
                items += array.optString(i)
            }
            playlists[playlistName] = items
        }
    }

    private fun savePlaylists() {
        val root = JSONObject()
        playlists.forEach { (name, items) ->
            root.put(name, JSONArray(items))
        }
        prefs.edit().putString(KEY_PLAYLISTS_JSON, root.toString()).apply()
    }

    private fun refreshPlaylistNames(selectName: String? = null) {
        playlistNamesAdapter.clear()
        playlistNamesAdapter.addAll(playlists.keys)
        playlistNamesAdapter.notifyDataSetChanged()

        if (playlistNamesAdapter.count == 0) {
            playlistTracksAdapter.clear()
            playlistTracksAdapter.notifyDataSetChanged()
            return
        }

        val indexToSelect = when {
            selectName != null -> playlists.keys.indexOf(selectName).takeIf { it >= 0 } ?: 0
            else -> spinnerPlaylists.selectedItemPosition.coerceAtLeast(0)
        }
        spinnerPlaylists.setSelection(indexToSelect)
    }

    private fun refreshPlaylistTracks() {
        val playlistName = spinnerPlaylists.selectedItem?.toString().orEmpty()
        val playlistUris = playlists[playlistName].orEmpty()
        val labels = playlistUris.map { uriString ->
            deviceTracks.firstOrNull { it.uri.toString() == uriString }?.displayLabel()
                ?: "No disponible en este dispositivo"
        }

        playlistTracksAdapter.clear()
        playlistTracksAdapter.addAll(labels)
        playlistTracksAdapter.notifyDataSetChanged()
        listPlaylistTracks.clearChoices()
    }

    private fun updateStatus(overrideText: String? = null) {
        txtStatus.text = overrideText ?: "GPS: listo"
    }

    private data class LocalTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val uri: Uri
    ) {
        fun displayLabel(): String {
            val minutes = durationMs / 1000 / 60
            val seconds = (durationMs / 1000) % 60
            return "$title - ${artist.ifBlank { "Artista desconocido" }} (${minutes}:${seconds.toString().padStart(2, '0')})"
        }
    }

    companion object {
        private const val KEY_PLAYLISTS_JSON = "native_playlists_json"
    }
}
