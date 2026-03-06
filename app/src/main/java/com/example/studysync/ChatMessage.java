package com.example.studysync;

public class ChatMessage {
    private String messageId; // stored locally, not in Firebase node
    private String senderId;
    private String senderName;
    private String text;
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String text, long timestamp) {
        this.senderId   = senderId;
        this.senderName = senderName;
        this.text       = text;
        this.timestamp  = timestamp;
    }

    public String getMessageId()               { return messageId; }
    public void   setMessageId(String id)      { this.messageId = id; }

    public String getSenderId()                { return senderId; }
    public void   setSenderId(String s)        { this.senderId = s; }

    public String getSenderName()              { return senderName; }
    public void   setSenderName(String s)      { this.senderName = s; }

    public String getText()                    { return text; }
    public void   setText(String t)            { this.text = t; }

    public long   getTimestamp()               { return timestamp; }
    public void   setTimestamp(long t)         { this.timestamp = t; }
}