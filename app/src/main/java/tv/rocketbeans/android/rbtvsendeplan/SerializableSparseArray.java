package tv.rocketbeans.android.rbtvsendeplan;

import android.util.SparseArray;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Serializable version of {@link android.util.SparseArray}
 * @param <E>
 */
public class SerializableSparseArray<E> extends SparseArray<E> implements Serializable {

    private static final long serialVersionUID = 1L;

    private void readObject(ObjectInputStream oi) throws IOException, ClassNotFoundException {
        Object[] data = (Object[]) oi.readObject();
        for (int i = 0; i < data.length; i++) {
            Object[] entry = (Object[]) data[i];
            this.append((Integer) entry[0], (E) entry[1]);
        }
    }

    private void writeObject(ObjectOutputStream os) throws IOException {
        Object[] data = new Object[this.size()];

        for (int i = 0; i < data.length; i++) {
            Object[] entry = {this.keyAt(i), this.valueAt(i)};
            data[i] = entry;
        }

        os.writeObject(data);
    }
}
