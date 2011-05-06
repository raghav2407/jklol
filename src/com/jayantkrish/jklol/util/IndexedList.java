package com.jayantkrish.jklol.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.NoSuchElementException;

/**
 * An indexed list is a way of maintaining an ordered set of elements
 * where each element has a unique numerical index and element lookups
 * can be performed in expected constant time.
 */ 
public class IndexedList<T> {

    private List<T> items;
    private Map<T, Integer> itemIndex;

    public IndexedList() {
	items = new ArrayList<T>();
	itemIndex = new HashMap<T, Integer>();
    }

    public IndexedList(Collection<T> toInsert) {
	items = new ArrayList<T>();
	itemIndex = new HashMap<T, Integer>();

	for (T item : toInsert) {
	    this.add(item);
	}
    }

    /**
     * Add a new element to this set. Note that there can only be a single
     * copy of any given item in the list.
     */
    public void add(T item) {
	if (itemIndex.containsKey(item)) {
	    // Items can only be added once
	    return;
	}
	itemIndex.put(item, items.size());
	items.add(item);
    }

    /**
     * Add each element of a set of elements to this list.
     */ 
    public void addAll(Collection<T> items) {
	for (T item : items) {
	    add(item);
	}
    }

    /**
     * Get the number of elements in the list.
     */
    public int size() {
	return items.size();
    }

    /**
     * True if the list contains the specified item.
     */ 
    public boolean contains(T item) {
	return itemIndex.containsKey(item);
    }

    /**
     * Get the index in the list of the specified item.
     */ 
    public int getIndex(T item) {
	if (!itemIndex.containsKey(item)) {
	    throw new NoSuchElementException();
	}
	return itemIndex.get(item);
    }

    /**
     * Get the item with the specified index.
     */ 
    public T get(int index) {
	if (index >= items.size() || index < 0) {
	    throw new IndexOutOfBoundsException();
	}
	return items.get(index);
    }

    /**
     * Get all of the items in the list
     */
    public List<T> items() {
	return Collections.unmodifiableList(items);
    }

    public String toString() {
	return items.toString();
    }
}