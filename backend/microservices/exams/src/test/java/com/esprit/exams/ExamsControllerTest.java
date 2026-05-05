package com.esprit.exams;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamsControllerTest {

    private final ExamsController controller = new ExamsController();

    @Test
    void sayHello_returnsExpectedMessage() {
        assertThat(controller.sayHello()).isEqualTo("Hello from Exams service");
    }
}
