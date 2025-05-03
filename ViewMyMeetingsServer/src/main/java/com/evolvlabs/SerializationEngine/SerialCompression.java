package com.evolvlabs.SerializationEngine;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author : Santiago Arellano
 * @date : 21st-Apr-2025
 * @description : The presente file implements a series of static methods, designed to help the
 * program in compressing the information from the JSON string created by the application, into a
 * more compressed format using MessagePack. The idea of this class is to presente a streamline way
 * of reducing packet size for quick, reliable, and secure data transfer between server and
 * client applications
 */
public class SerialCompression {

    public static byte[] packSerializedStringIntoByteArray(String jsonOutput){
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
            MessagePacker packer = MessagePack.newDefaultPacker(
                    outputStream);
            if (jsonOutput == null){
                return null;
            }
            packer.packString(jsonOutput);
            packer.close();
            return outputStream.toByteArray();
        } catch (IOException e) {
            System.out.println("Error while serializing the string into MessagePack");
            e.printStackTrace();
            return null;
        }
    }

    public static String unpackSerializedStringFromByteArray(byte[] byteArray){
        if (byteArray != null){
            try(MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(byteArray)){
                return unpacker.unpackString();
            } catch (IOException e) {
                System.out.println("Error while deserializing the string from MessagePack");
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }
}
