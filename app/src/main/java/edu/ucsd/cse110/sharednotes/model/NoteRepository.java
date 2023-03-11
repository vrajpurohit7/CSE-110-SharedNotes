package edu.ucsd.cse110.sharednotes.model;

import android.util.JsonReader;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class NoteRepository {
    private final NoteDao dao;

    private final NoteAPI api;
    private ScheduledFuture<?> poller; // what could this be for... hmm?

    private Future<?> noteFuture;
    // LiveData variable which contains the latest real time value.
    private MutableLiveData<Note> realNoteData;

    private static final MediaType JSON = MediaType.parse("aplication/json; charset=utf-8");

    public NoteRepository(NoteDao dao, NoteAPI api) {
        this.dao = dao;
        this.api = api;
    }


    private OkHttpClient client;

    public void NoteAPI() {
        this.client = new OkHttpClient();
    }

    // Synced Methods
    // ==============

    /**
     * This is where the magic happens. This method will return a LiveData object that will be
     * updated when the note is updated either locally or remotely on the server. Our activities
     * however will only need to observe this one LiveData object, and don't need to care where
     * it comes from!
     * <p>
     * This method will always prefer the newest version of the note.
     *
     * @param title the title of the note
     * @return a LiveData object that will be updated when the note is updated locally or remotely.
     */
    public LiveData<Note> getSynced(String title) {
        var note = new MediatorLiveData<Note>();

        Observer<Note> updateFromRemote = theirNote -> {
            var ourNote = note.getValue();
            if (theirNote == null) return; // do nothing
            if (ourNote == null || ourNote.version < theirNote.version) {
                upsertLocal(theirNote, false);
            }
        };

        // If we get a local update, pass it on.
        note.addSource(getLocal(title), note::postValue);
        // If we get a remote update, update the local version (triggering the above observer)
        note.addSource(getRemote(title), updateFromRemote);

        return note;
    }

    public void upsertSynced(Note note) {
        upsertLocal(note);
        upsertRemote(note);
    }

    // Local Methods
    // =============

    public LiveData<Note> getLocal(String title) {
        return dao.get(title);
    }

    public LiveData<List<Note>> getAllLocal() {
        return dao.getAll();
    }

    public void upsertLocal(Note note, boolean incrementVersion) {
        // We don't want to increment when we sync from the server, just when we save.
        if (incrementVersion) note.version = note.version + 1;
        note.version = note.version + 1;
        dao.upsert(note);
    }

    public void upsertLocal(Note note) {
        upsertLocal(note, true);
    }

    public void deleteLocal(Note note) {
        dao.delete(note);
    }

    public boolean existsLocal(String title) {
        return dao.exists(title);
    }

    // Remote Methods
    // ==============

    public LiveData<Note> getRemote(String title) {
        // Implement getRemote!
        // Set up polling background thread (MutableLiveData?)
        // Refer to TimerService from https://github.com/DylanLukes/CSE-110-WI23-Demo5-V2.

        // Cancel any previous poller if it exists.
        if (this.poller != null && !this.poller.isCancelled()) {
            poller.cancel(true);
        }

        // Set up a background thread that will poll the server every 3 seconds.
        /*
        * only poll one note title at a time.
        * Save into a member variable the ScheduledFuture returned by executor.schedule(...) each time getRemote is called.
        * When it's called again, call cancel() on the future. (Reuse the executor, don't create a new one each call).
        */
        Note newNote = null;
        newNote = api.getNote(title);
        realNoteData = new MutableLiveData<>();
        var executor = Executors.newSingleThreadScheduledExecutor();
         noteFuture = executor.scheduleAtFixedRate(() -> {
             var note = api.getNote(title);
             realNoteData.postValue(note);
        }, 0, 3000, TimeUnit.MILLISECONDS);

        // You may (but don't have to) want to cache the LiveData's for each title, so that
        // you don't create a new polling thread every time you call getRemote with the same title.
        // You don't need to worry about killing background threads.

        return realNoteData;
    }

    public void upsertRemote(Note note) {
        // Implement upsertRemote!
        // it's supposed to upload data onto database
        // created a new json object, fill it with information, turn json into note, and put that note onto server

        var executor = Executors.newSingleThreadExecutor();
        noteFuture = executor.submit(() -> {
            JsonObject JSon = new JsonObject();
            JSon.addProperty("content", note.content);
            JSon.addProperty("version", note.version + 1);

            api.putNote(note);
        });


//        String JSONToSave = note.toJSON();
//        RequestBody body = RequestBody.create(JSONToSave, JSON);
//
//        // URLs cannot contain spaces, so we replace them with %20.
//        String noteMsg = note.title.replace(" ", "%20");
//        var request = new Request.Builder()
//                .url("https://sharednotes.goto.ucsd.edu/notes/" + noteMsg)
//                .method("PUT", body)
//                .build();
//
//        try (var response = client.newCall(request).execute()) {
//            assert response.body() != null;
//            Log.i("SAVE", response.body().string());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}
