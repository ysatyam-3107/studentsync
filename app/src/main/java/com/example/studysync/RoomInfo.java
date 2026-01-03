package com.example.studysync;

public class RoomInfo {
    private String roomCode;
    private int memberCount;
    private long createdAt;
    private boolean isHost;

    public RoomInfo() {}

    public RoomInfo(String roomCode, int memberCount, Long createdAt, boolean isHost) {
        this.roomCode = roomCode;
        this.memberCount = memberCount;
        this.createdAt = createdAt != null ? createdAt : 0;
        this.isHost = isHost;
    }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isHost() { return isHost; }
    public void setHost(boolean host) { isHost = host; }
}