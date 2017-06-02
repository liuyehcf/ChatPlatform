package org.liuyehcf.chat.reader;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Created by Liuye on 2017/5/29.
 */
public class MessageBuffer {

    /**
     * 缓存容量
     */
    private int capacity;

    /**
     * 指向当前位置，左边是已缓存数据，右边是未使用区域
     * [0,capacity-1]
     */
    private int position;

    /**
     * 保存内容的字节数组
     */
    private byte[] byteArray;

    private static final int _1K = 1000;

    private static final int SMALL_CAPACITY = 4 * _1K;

    private static final int MIDDLE_CAPACITY = 32 * _1K;

    private static final int LARGE_CAPACITY = 128 * _1K;

    MessageBuffer() {
        capacity = SMALL_CAPACITY;
        position = 0;
        byteArray = new byte[capacity];
    }

    public void buffer(ByteBuffer byteBuffer) {
        byteBuffer.flip();
        int remain = remain();
        int needCache = byteBuffer.remaining();
        if (remain < needCache) {
            expandSelf(getPosition() + needCache);
        }

        while (byteBuffer.remaining() > 0) {
            save(byteBuffer.get());
        }
    }

    private void save(byte b) {
        byteArray[position++] = b;
    }

    public int getPosition() {
        return position;
    }

    public byte getByte(int pos) {
        return byteArray[pos];
    }

    private int remain() {
        return capacity - position;
    }

    private void expandSelf(int minimumCapacity) {
        if (minimumCapacity >= LARGE_CAPACITY) {
            expandAndCopy(minimumCapacity);
        } else if (capacity == SMALL_CAPACITY) {
            expandAndCopy(MIDDLE_CAPACITY);
        } else if (capacity == MIDDLE_CAPACITY) {
            expandAndCopy(LARGE_CAPACITY);
        }
    }

    private void expandAndCopy(int expandCapacity) {
        capacity = expandCapacity;
        byte[] newByteArray = new byte[capacity];
        System.arraycopy(byteArray, 0, newByteArray, 0, getPosition() + 1);
        byteArray = newByteArray;
    }

    /**
     * 返回已缓存的内容
     *
     * @return
     */
    public String getBufferedString() {
        byte[] contentBytes = new byte[position];
        System.arraycopy(byteArray, 0, contentBytes, 0, position);
        try {
            return new String(contentBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(System.out);
            throw new RuntimeException("UTF-8编码失败");
        }
    }

    public void shiftLeft(int shiftLength) {
        for (int i = shiftLength; i <= position; i++) {
            byteArray[i - shiftLength] = byteArray[i];
        }
        position = position - shiftLength;
    }

    @Override
    public String toString() {
        return getBufferedString();
    }
}
