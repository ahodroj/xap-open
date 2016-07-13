/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.j_spaces.core;

import com.gigaspaces.internal.utils.concurrent.GSThread;

/**
 * @author idan
 * @since 9.5
 */
@com.gigaspaces.api.InternalApi
public class ProcessMemoryManager implements IProcessMemoryManager {
    private static final Runtime _runtime = Runtime.getRuntime();

    private static final long _totalMemory = (_runtime.totalMemory() == _runtime.maxMemory()) ? _runtime.totalMemory() : -1;
    private static final long _maximumMemory = _runtime.maxMemory();
    private static final boolean _asyncMemorySamplerEnabled = Boolean.getBoolean("com.gs.asyncMemorySampler");
    private static final long _samplerThreadSleepDurationInMs = Long.getLong("com.gs.asyncMemorySampler.interval", 10);
    private static volatile boolean _samplerThreadShouldRun = false;
    private static volatile long _freeMemory = _runtime.freeMemory();

    public static final IProcessMemoryManager INSTANCE = new ProcessMemoryManager();

    private ProcessMemoryManager() {
        if (_asyncMemorySamplerEnabled) {
            Thread samplerThread = new GSThread(new MemorySampler(), "ProcessMemoryManager");
            samplerThread.setDaemon(true);
            samplerThread.start();
        }
        System.out.println("com.gs.asyncMemorySampler=" + _asyncMemorySamplerEnabled);
    }

    public void performGC() {
        _runtime.gc();
    }


    public double getMemoryUsagePercentage(boolean directCheck) {
        return (getMemoryUsage(directCheck) * 100.0) / getMaximumMemory();
    }

    public long getMemoryUsage() {
        return getMemoryUsage(false);
    }

    public long getMemoryUsage(boolean directCheck) {
        long totalMemory = _totalMemory == -1 ? _runtime.totalMemory() : _totalMemory;
        return totalMemory - getFreeMemory(directCheck);
    }

    public long getMaximumMemory() {
        return _maximumMemory;
    }
    public long getFreeMemory() {
        return getFreeMemory(false);
    }

    @Override
    public boolean isAsyncCheckEnabled() {
        return _asyncMemorySamplerEnabled;
    }

    public long getFreeMemory(boolean directCheck) {
        if (_asyncMemorySamplerEnabled && !directCheck) {
            _samplerThreadShouldRun = true;
            return _freeMemory;
        } else {
            _freeMemory = _runtime.freeMemory();
            return _freeMemory;
        }
    }

    public class MemorySampler implements Runnable {
        @Override
        public void run() {
            while(true) {
                while (!_samplerThreadShouldRun) {
                    try {
                        Thread.sleep(_samplerThreadSleepDurationInMs);
                    } catch (InterruptedException e) {
                    }
                }
                while(_samplerThreadShouldRun) {
                    _samplerThreadShouldRun = false;
                    _freeMemory = _runtime.freeMemory();
                }
            }
        }
    }

}
