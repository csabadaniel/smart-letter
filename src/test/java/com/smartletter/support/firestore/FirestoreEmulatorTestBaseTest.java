package com.smartletter.support.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.firestore.DocumentSnapshot;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirestoreEmulatorTestBaseTest extends FirestoreEmulatorTestBase {

    private static final String DOCUMENT_PATH = "appSettings/configuration/delivery/main";

    @Test
    @Order(1)
    void seedFixtureWritesDocument() throws ExecutionException, InterruptedException {
        seedFixture("fixtures/firestore/delivery-configuration.json");

        DocumentSnapshot snapshot = firestore().document(DOCUMENT_PATH).get().get();
        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.getString("recipientEmail")).isEqualTo("ops@example.com");
        assertThat(snapshot.getString("promptSha256"))
                .isEqualTo("9561b94cb786c7b5834e01649ffe362d49c69abf5be768456d6f17707c53ef98");
    }

    @Test
    @Order(2)
    void emulatorIsEmptyBetweenTests() throws ExecutionException, InterruptedException {
        DocumentSnapshot snapshot = firestore().document(DOCUMENT_PATH).get().get();
        assertThat(snapshot.exists()).isFalse();
    }
}
