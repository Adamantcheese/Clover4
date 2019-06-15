package com.github.adamantcheese.chan.core.repository;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.mapper.ThreadMapper;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.inject.Inject;

public class SavedThreadLoaderRepository {
    public static final String THREAD_FILE_NAME = "thread.json";
    private static final int MAX_THREAD_SIZE_BYTES = 50 * 1024 * 1024; // 50mb

    private Gson gson;

    @Inject
    public SavedThreadLoaderRepository(Gson gson) {
        this.gson = gson;
    }

    @Nullable
    public SerializableThread loadOldThreadFromJsonFile(File threadSaveDir) throws IOException {
        File threadFile = new File(threadSaveDir, THREAD_FILE_NAME);
        if (!threadFile.exists()) {
            return null;
        }

        String json;

        try (RandomAccessFile raf = new RandomAccessFile(threadFile, "rw")) {
            int size = raf.readInt();
            if (size <= 0 || size > MAX_THREAD_SIZE_BYTES) {
                throw new IOException("Old serialized thread takes way too much space: " + size +
                        " bytes. You are not trying to save an infinite or sticky thread, right? " +
                        "It's not supported.");
            }

            byte[] bytes = new byte[size];
            raf.read(bytes);

            json = new String(bytes, StandardCharsets.UTF_8);
        }

        return gson.fromJson(json, SerializableThread.class);
    }

    public void savePostsToJsonFile(
            @Nullable SerializableThread oldSerializableThread,
            List<Post> posts,
            File threadSaveDir) throws IOException {
        SerializableThread serializableThread;

        if (oldSerializableThread != null) {
            // Merge with old posts if there are any
            serializableThread = oldSerializableThread.merge(posts);
        } else {
            // Use only the new posts
            serializableThread = ThreadMapper.toSerializableThread(posts);
        }

        String threadJson = gson.toJson(serializableThread);

        File threadFile = new File(threadSaveDir, THREAD_FILE_NAME);
        if (!threadFile.exists() && !threadFile.createNewFile()) {
            throw new IOException("Could not create the thread file " +
                    "(path: " + threadFile.getAbsolutePath() + ")");
        }

        // Update the thread file
        try (RandomAccessFile raf = new RandomAccessFile(threadFile, "rw")) {
            byte[] bytes = threadJson.getBytes(StandardCharsets.UTF_8);
            raf.writeInt(bytes.length);
            raf.write(bytes);
        }
    }

}