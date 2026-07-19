package com.veil.events;

/**
 * Whether an event may be broadcast to everyone or only to a specific audience.
 * The redaction boundary uses this to decide who may ever see an event.
 */
public enum Visibility {
    PUBLIC,
    PRIVATE
}
