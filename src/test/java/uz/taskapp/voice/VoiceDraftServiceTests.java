package uz.taskapp.voice;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.common.ApiException;
import uz.taskapp.group.GroupEntity;
import uz.taskapp.group.GroupRepository;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.workspace.WorkspaceMemberRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceDraftServiceTests {
    private static final Long WORKSPACE_ID = 42L;
    private static final Long USER_ID = 7L;

    private final VoiceModelClient voiceModelClient = mock(VoiceModelClient.class);
    private final WorkspaceMemberRepository workspaceMemberRepository = mock(WorkspaceMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final VoiceDraftService service = new VoiceDraftService(
            voiceModelClient,
            workspaceMemberRepository,
            userRepository,
            groupRepository,
            objectMapper,
            new PendingVoiceDraftStore()
    );

    @Test
    void filtersOutAssigneeIdsNotInCandidateList() {
        setUpMembers();
        setUpModelResponse(new VoiceDraftPayload(
                "Ertaga Aliga tayinla", "Hisobotni tayyorlash", "", null,
                List.of(1L, 999L), List.of(), null, null, null, false));

        VoiceDraftResponse response = service.createDraft(USER_ID, WORKSPACE_ID, validAudio());

        assertEquals(List.of(1L), response.assigneeIds());
    }

    @Test
    void snapsReminderMinutesToNearestPresetWhenDueAtPresent() {
        setUpMembers();
        setUpModelResponse(new VoiceDraftPayload(
                "Ertaga hisobot tayyorla, yarim soatlar oldin eslat", "Hisobotni tayyorlash", "",
                "2026-07-28T12:00:00Z", List.of(), List.of(), null, null, 40L, false));

        VoiceDraftResponse response = service.createDraft(USER_ID, WORKSPACE_ID, validAudio());

        assertEquals(30L, response.reminderMinutes());
    }

    @Test
    void ignoresReminderMinutesWhenNoDueAt() {
        setUpMembers();
        setUpModelResponse(new VoiceDraftPayload(
                "Hisobot tayyorla, 30 daqiqa oldin eslat", "Hisobotni tayyorlash", "", null,
                List.of(), List.of(), null, null, 30L, false));

        VoiceDraftResponse response = service.createDraft(USER_ID, WORKSPACE_ID, validAudio());

        assertNull(response.reminderMinutes());
    }

    @Test
    void filtersOutGroupIdNotInCandidateList() {
        setUpMembers();
        GroupEntity marketingGroup = mock(GroupEntity.class);
        when(marketingGroup.getId()).thenReturn(5L);
        when(marketingGroup.getName()).thenReturn("Marketing");
        when(groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(WORKSPACE_ID))
                .thenReturn(List.of(marketingGroup));
        setUpModelResponse(new VoiceDraftPayload(
                "Marketing guruhida hisobot tayyorla", "Hisobotni tayyorlash", "", null,
                List.of(), List.of(), 999L, null, null, false));

        VoiceDraftResponse response = service.createDraft(USER_ID, WORKSPACE_ID, validAudio());

        assertNull(response.groupId());
    }

    @Test
    void acceptsMatchedGroupIdInCandidateList() {
        setUpMembers();
        GroupEntity marketingGroup = mock(GroupEntity.class);
        when(marketingGroup.getId()).thenReturn(5L);
        when(marketingGroup.getName()).thenReturn("Marketing");
        when(groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(WORKSPACE_ID))
                .thenReturn(List.of(marketingGroup));
        setUpModelResponse(new VoiceDraftPayload(
                "Marketing guruhida hisobot tayyorla", "Hisobotni tayyorlash", "", null,
                List.of(), List.of(), 5L, null, null, false));

        VoiceDraftResponse response = service.createDraft(USER_ID, WORKSPACE_ID, validAudio());

        assertEquals(5L, response.groupId());
    }

    @Test
    void invalidDueAtIsoFallsBackToNull() {
        setUpMembers();
        setUpModelResponse(new VoiceDraftPayload(
                "Hisobot tayyorla", "Hisobotni tayyorlash", "", "not-a-date",
                List.of(), List.of(), null, null, null, false));

        VoiceDraftResponse response = service.createDraft(USER_ID, WORKSPACE_ID, validAudio());

        assertNull(response.dueAt());
    }

    @Test
    void noSpeechDetectedThrowsUnprocessableEntity() {
        setUpMembers();
        setUpModelResponse(new VoiceDraftPayload(
                "", "", "", null, List.of(), List.of(), null, null, null, true));

        ApiException exception = assertThrows(ApiException.class, () -> service.createDraft(USER_ID, WORKSPACE_ID, validAudio()));

        assertEquals("VOICE_NO_SPEECH_DETECTED", exception.getCode());
    }

    private void setUpMembers() {
        when(voiceModelClient.configured()).thenReturn(true);
        when(workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(WORKSPACE_ID, USER_ID))
                .thenReturn(true);
        WorkspaceMemberEntity memberOne = new WorkspaceMemberEntity(WORKSPACE_ID, 1L, "MEMBER");
        WorkspaceMemberEntity memberTwo = new WorkspaceMemberEntity(WORKSPACE_ID, 2L, "MEMBER");
        when(workspaceMemberRepository.findAllByWorkspaceIdAndActiveTrue(WORKSPACE_ID))
                .thenReturn(List.of(memberOne, memberTwo));

        UserEntity userOne = mock(UserEntity.class);
        when(userOne.getId()).thenReturn(1L);
        when(userOne.getFirstName()).thenReturn("Ali");
        when(userOne.getUsername()).thenReturn("ali");

        UserEntity userTwo = mock(UserEntity.class);
        when(userTwo.getId()).thenReturn(2L);
        when(userTwo.getFirstName()).thenReturn("Vali");
        when(userTwo.getUsername()).thenReturn("vali");

        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(userOne, userTwo));
        when(groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(WORKSPACE_ID)).thenReturn(List.of());
    }

    private void setUpModelResponse(VoiceDraftPayload payload) {
        when(voiceModelClient.transcribeAndExtract(any(), any(), any())).thenReturn(payload);
    }

    private MultipartFile validAudio() {
        return new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[]{1, 2, 3, 4});
    }
}
