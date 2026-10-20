package com.krniket.platform.data;

import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class FirestoreStore {
    private final ObjectProvider<Firestore> firestoreProvider;

    public FirestoreStore(ObjectProvider<Firestore> firestoreProvider) {
        this.firestoreProvider = firestoreProvider;
    }

    private Firestore firestore() {
        Firestore firestore = firestoreProvider.getIfAvailable();
        if (firestore == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firestore is not configured.");
        return firestore;
    }

    public Optional<Map<String, Object>> get(String collection, String id) {
        try {
            DocumentSnapshot snapshot = firestore().collection(collection).document(id).get().get();
            return snapshot.exists() ? Optional.of(withId(snapshot)) : Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    public String create(String collection, Map<String, Object> values) {
        try {
            var document = firestore().collection(collection).document();
            document.set(withoutId(values)).get();
            return document.getId();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    public void set(String collection, String id, Map<String, Object> values) {
        try {
            firestore().collection(collection).document(id).set(withoutId(values)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    public void patch(String collection, String id, Map<String, Object> values) {
        if (values.isEmpty()) return;
        try {
            firestore().collection(collection).document(id).update(withoutId(values)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    public void delete(String collection, String id) {
        try {
            firestore().collection(collection).document(id).delete().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    public List<Map<String, Object>> find(String collection, Map<String, Object> filters) {
        return listQuery(query(collection, filters), 0, 1000);
    }

    public List<Map<String, Object>> page(String collection, Map<String, Object> filters, int page, int size) {
        return listQuery(query(collection, filters), page, size);
    }

    public long count(String collection, Map<String, Object> filters) {
        try {
            AggregateQuerySnapshot count = query(collection, filters).count().get().get();
            return count.getCount();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    private Query query(String collection, Map<String, Object> filters) {
        Query query = firestore().collection(collection);
        for (var filter : filters.entrySet()) query = query.whereEqualTo(filter.getKey(), filter.getValue());
        return query;
    }

    private List<Map<String, Object>> listQuery(Query query, int page, int size) {
        try {
            List<QueryDocumentSnapshot> documents = query.orderBy(FieldPath.documentId())
                    .offset(page * size).limit(size).get().get().getDocuments();
            List<Map<String, Object>> values = new ArrayList<>(documents.size());
            for (QueryDocumentSnapshot document : documents) values.add(withId(document));
            return values;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception);
        }
    }

    private Map<String, Object> withId(DocumentSnapshot snapshot) {
        Map<String, Object> data = new HashMap<>(snapshot.getData());
        data.put("id", snapshot.getId());
        return data;
    }

    private Map<String, Object> withoutId(Map<String, Object> values) {
        Map<String, Object> data = new HashMap<>(values);
        data.remove("id");
        return data;
    }

    private ResponseStatusException unavailable(Exception exception) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firestore request failed.", exception);
    }
}
