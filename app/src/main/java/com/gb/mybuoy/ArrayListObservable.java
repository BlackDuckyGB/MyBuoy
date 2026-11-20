/*
 * MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
 * Copyright (C) 2026 Golden-Block
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * For commercial licensing (use without AGPL obligations), contact:
 * licence@golden-block.fr
 */
package com.gb.mybuoy;

import android.os.Build;

import java.util.ArrayList;
import java.util.Comparator;

import androidx.annotation.RequiresApi;

public class ArrayListObservable<T> extends ArrayList<T> implements ListObservable<T> {

    // Guards the list content and the observer list, and NOTHING ELSE: observers are always called
    // after it is released, never from inside it. They used to run under this lock plus
    // synchronized(observer), on top of whatever the caller held (AppManager's write lock), and
    // reached data binding and the list adapters from there.
    private Object lock = new Object();
    private final ArrayList<OnPropertyChangedCallback> mObservers;

    public ArrayListObservable() {
        super();
        mObservers = new ArrayList<OnPropertyChangedCallback>();
    }


    @Override
    public boolean add(T o) {
        boolean bRet = addInList(o);

        callObservers();

        return bRet;
    }

    @Override
    public boolean remove(Object o) {
        boolean bRet = removeFromList(o);

        callObservers();

        return bRet;
    }

    @Override
    public void clear() {
        synchronized (lock) {
            super.clear();
        }

        callObservers();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void sort(Comparator<? super T> c) {
        sortList(c);

        callObservers();
    }

    @Override
    public boolean addQuietly(T o) {
        return addInList(o);
    }

    @Override
    public boolean removeQuietly(Object o) {
        return removeFromList(o);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void sortQuietly(Comparator<? super T> c) {
        sortList(c);
    }

    @Override
    public void clearQuietly() {
        synchronized (lock) {
            super.clear();
        }
    }

    @Override
    public void notifyObservers() {
        callObservers();
    }

    private boolean addInList(T o) {
        boolean bRet = false;

        synchronized (lock) {
            if( !super.contains(o))
                bRet = super.add(o);
        }

        return bRet;
    }

    private boolean removeFromList(Object o) {
        boolean bRet = false;

        synchronized (lock) {
            if( super.contains(o))
                bRet = super.remove(o);
        }

        return bRet;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void sortList(Comparator<? super T> c) {
        synchronized (lock) {
            super.sort(c);
        }
    }

    // Copies the observers under the lock, calls them outside it.
    private void callObservers() {
        ArrayList<OnPropertyChangedCallback> observers;

        synchronized (lock) {
            observers = new ArrayList<OnPropertyChangedCallback>(mObservers);
        }

        for (OnPropertyChangedCallback observer : observers) {
            observer.onPropertyChanged(this, BR._all);
        }
    }

    @Override
    public void addOnPropertyChangedCallback(OnPropertyChangedCallback callback) {

        synchronized (lock) {
            if (!mObservers.contains(callback)) {
                mObservers.add(callback);
            }
        }

    }

    @Override
    public void removeOnPropertyChangedCallback(OnPropertyChangedCallback callback) {

        synchronized (lock) {
            if (mObservers.contains(callback)) {
                mObservers.remove(callback);
            }
        }

    }
}
