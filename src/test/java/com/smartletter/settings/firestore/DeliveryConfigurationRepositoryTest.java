package com.smartletter.settings.firestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.Transaction;
import com.smartletter.settings.config.DeliveryConfigurationProperties;
import java.time.Instant;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryConfigurationRepositoryTest {

    private static final String DOCUMENT_PATH = "appSettings/configuration/delivery/main";

    @Mock
    private Firestore firestore;

    @Mock
    private DocumentReference documentReference;

    @Mock
    private Transaction transaction;

    @Mock
    private DocumentSnapshot snapshot;

    @Mock
    private DocumentSnapshot updatedSnapshot;

    private DeliveryConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        DeliveryConfigurationProperties properties = new DeliveryConfigurationProperties();
        properties.setCollectionPath(DOCUMENT_PATH);
        properties.setCacheTtlSeconds(60);
        repository = new DeliveryConfigurationRepository(firestore, properties);
        when(firestore.document(DOCUMENT_PATH)).thenReturn(documentReference);
    }

    @Test
    void findConfigurationReturnsEmptyWhenDocumentMissing() throws Exception {
        when(documentReference.get()).thenReturn(immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(false);

        assertThat(repository.findConfiguration()).isEmpty();
    }

    @Test
    void findConfigurationReturnsDocument() throws Exception {
        Timestamp timestamp = Timestamp.ofTimeSecondsAndNanos(10, 0);
        when(documentReference.get()).thenReturn(immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("recipientEmail")).thenReturn("ops@example.com");
        when(snapshot.getString("llmPrompt")).thenReturn("Prompt body");
        when(snapshot.getString("promptSha256")).thenReturn("hash");
        when(snapshot.getLong("version")).thenReturn(2L);
        when(snapshot.getString("updatedBy")).thenReturn("ops:local");
        when(snapshot.getTimestamp("updatedAt")).thenReturn(timestamp);

        assertThat(repository.findConfiguration())
                .isPresent()
                .get()
                .satisfies(document -> {
                    assertThat(document.recipientEmail()).isEqualTo("ops@example.com");
                    assertThat(document.llmPrompt()).isEqualTo("Prompt body");
                    assertThat(document.promptSha256()).isEqualTo("hash");
                    assertThat(document.version()).isEqualTo(2L);
                    assertThat(document.updatedBy()).isEqualTo("ops:local");
                    assertThat(document.updatedAt()).isEqualTo(Instant.ofEpochSecond(10));
                });
    }

    @Test
    void upsertWritesDocumentAndReturnsLatestState() throws Exception {
        mockTransactionExecution();
        when(transaction.get(documentReference)).thenReturn(immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getLong("version")).thenReturn(2L);
        String promptHash = DigestUtils.sha256Hex("Prompt body");
        Timestamp timestamp = Timestamp.ofTimeSecondsAndNanos(20, 0);
        when(documentReference.get()).thenReturn(immediateFuture(updatedSnapshot));
        when(updatedSnapshot.exists()).thenReturn(true);
        when(updatedSnapshot.getString("recipientEmail")).thenReturn("ops@example.com");
        when(updatedSnapshot.getString("llmPrompt")).thenReturn("Prompt body");
        when(updatedSnapshot.getString("promptSha256")).thenReturn(promptHash);
        when(updatedSnapshot.getLong("version")).thenReturn(3L);
        when(updatedSnapshot.getString("updatedBy")).thenReturn("ops:local");
        when(updatedSnapshot.getTimestamp("updatedAt")).thenReturn(timestamp);

        DeliveryConfigurationDocument document = repository.upsert(
                new DeliveryConfigurationWriteModel("ops@example.com", "Prompt body", "ops:local", 2L));

        assertThat(document.version()).isEqualTo(3L);
        assertThat(document.promptSha256()).isEqualTo(promptHash);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(mapClass());
        verify(transaction).set(eq(documentReference), payloadCaptor.capture(), eq(SetOptions.merge()));
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload)
                .containsEntry("recipientEmail", "ops@example.com")
                .containsEntry("llmPrompt", "Prompt body")
            .containsEntry("promptSha256", promptHash)
                .containsEntry("updatedBy", "ops:local");
        assertThat(payload.get("version")).isNotNull();
    }

    @Test
    void upsertThrowsWhenVersionConflicts() throws Exception {
        mockTransactionExecution();
        when(transaction.get(documentReference)).thenReturn(immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getLong("version")).thenReturn(3L);

        assertThatThrownBy(() -> repository.upsert(
                new DeliveryConfigurationWriteModel("ops@example.com", "Prompt body", "ops:local", 2L)))
                .isInstanceOf(ConfigVersionConflictException.class);
    }

    private void mockTransactionExecution() {
        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            com.google.cloud.firestore.Transaction.Function<Void> function = invocation.getArgument(0);
            try {
                function.updateCallback(transaction);
                return ApiFutures.immediateFuture(null);
            } catch (Exception ex) {
                SettableApiFuture<Void> failed = SettableApiFuture.create();
                failed.setException(ex);
                return failed;
            }
        });
    }

    private <T> ApiFuture<T> immediateFuture(T value) {
        return ApiFutures.immediateFuture(value);
    }

    @SuppressWarnings("unchecked")
    private Class<Map<String, Object>> mapClass() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }
}
