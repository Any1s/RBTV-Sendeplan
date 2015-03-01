package de.mbdevelopment.android.rbtvsendeplan;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Singleton providing application-wide locks for file access.
 */
public class FileLockHolder {

    /**
     * Maps filenames to their locks
     */
    private final Map<String, ReentrantReadWriteLock> fileToLockMap;

    // Private constructor. Prevents instantiation from other classes.
    private FileLockHolder() {
        fileToLockMap = new HashMap<>();
    }

    /**
     * Implements Bill Pugh's version of the singleton pattern instantiation
     */
    private static class InstanceHolder {
        private static final FileLockHolder INSTANCE = new FileLockHolder();
    }

    /**
     * Gets the singleton FileLockHolder
     * @return FileLockHolder instance
     */
    public static FileLockHolder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Acquires a read lock for a file.
     * @param filename Filename of the file the lock is for
     * @return A read lock for the file
     */
    public Lock getReadLock(String filename) {
        if (!fileToLockMap.containsKey(filename)) {
            fileToLockMap.put(filename, new ReentrantReadWriteLock());
        }

        return fileToLockMap.get(filename).readLock();
    }

    /**
     * Acquires a write lock for a file.
     * @param filename Filename of the file the lock is for
     * @return A write lock for the file
     */
    public Lock getWriteLock(String filename) {
        if (!fileToLockMap.containsKey(filename)) {
            fileToLockMap.put(filename, new ReentrantReadWriteLock());
        }

        return fileToLockMap.get(filename).writeLock();
    }
}
