package ca.mcmaster.capstone.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import ca.mcmaster.capstone.monitoralgorithm.Token;

import lombok.NonNull;

public class CollectionUtils {

    public static <T> Collection<T> filter(@NonNull final Iterable<? extends T> iterable, @NonNull final Predicate<T> predicate) {
        final List<T> results = new ArrayList<>();
        for (final T t : iterable) {
            if (predicate.apply(t)) {
                results.add(t);
            }
        }
        return results;
    }

    public static <T> void each(@NonNull final Collection<? extends T> collection, @NonNull final Consumer<T> consumer) {
        for (final T t : collection) {
            consumer.consume(t);
        }
    }

    //TODO: implement map
    public static byte[] serializeObject(Object obj) throws IOException{

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		try {
			out = new ObjectOutputStream(bos);   
			try {
				out.writeObject(obj);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			byte[] yourBytes = bos.toByteArray();
			return yourBytes;
		}
		finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException ex) {
				// ignore close exception
			}
			try {
				bos.close();
			} catch (IOException ex) {
				// ignore close exception
			}
		}
	}
    
    public static Object deserializeObject(byte[] message) throws IOException, ClassNotFoundException{
    	ByteArrayInputStream bis = new ByteArrayInputStream(message);
		ObjectInput in = null;
		try {
			in = new ObjectInputStream(bis);
			Object o = in.readObject(); 
			 
			return o;
		} 
		finally 
		{
			try {
				bis.close();
			} catch (IOException ex) {
				// ignore close exception
			}
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException ex) {
				// ignore close exception
			}
		}
    }
}
