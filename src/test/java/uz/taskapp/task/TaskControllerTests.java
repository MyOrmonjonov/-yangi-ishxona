package uz.taskapp.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import uz.taskapp.auth.AuthInterceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TaskControllerTests {
    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        mockMvc = standaloneSetup(new TaskController(taskService)).build();
    }

    @Test
    void createsJsonTaskThroughJsonHandler() throws Exception {
        when(taskService.create(eq(1L), any(CreateTaskRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/tasks")
                        .requestAttr(AuthInterceptor.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workspaceId":1,"title":"Sinov","status":"NEW","priority":"NORMAL",
                                 "visibility":"PERSONAL","assigneeIds":[1],"checklist":[]}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void createsTaskWithImageThroughMultipartHandler() throws Exception {
        when(taskService.create(eq(1L), any(CreateTaskRequest.class), anyList())).thenReturn(null);
        MockMultipartFile task = new MockMultipartFile("task", "task.json", MediaType.APPLICATION_JSON_VALUE,
                """
                        {"workspaceId":1,"title":"Rasmli sinov","status":"NEW","priority":"NORMAL",
                         "visibility":"PERSONAL","assigneeIds":[1],"checklist":[]}
                        """.getBytes());
        MockMultipartFile image = new MockMultipartFile("files", "photo.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/tasks")
                        .file(task)
                        .file(image)
                        .requestAttr(AuthInterceptor.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isOk());
    }
}
