/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes
 * shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.processing.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadUtil {
    public static ExecutorService newThreadExecutor(int threads, String name) {
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName(name);
            return t;
        });
    }

    public static ExecutorService newSingleThreadExecutor(String name) {
        return newThreadExecutor(1, name);
    }
}
