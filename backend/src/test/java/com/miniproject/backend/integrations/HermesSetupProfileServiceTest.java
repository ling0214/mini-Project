package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HermesSetupProfileServiceTest {

    private final HermesSetupProfileRepository repository = mock(HermesSetupProfileRepository.class);
    private final HermesIncidentReader incidentReader = mock(HermesIncidentReader.class);
    private final HermesSetupProfileService service = new HermesSetupProfileService(repository, incidentReader);

    @Test
    void savingWithAHermesHomeProvisionsItsFolderSkeleton(@TempDir Path tempDir) {
        Path hermesHome = tempDir.resolve("new-project-hermes-home");
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.save(null, "New Project", new HermesSetupProfileService.HermesSetupProfileSaveRequest(
                "C:/repos/new-project", List.of("discord"), null, null, null, List.of(),
                null, null, null, null, false, null, hermesHome.toString()));

        verify(incidentReader).provisionHermesHome(hermesHome.toString());
    }

    @Test
    void savingWithoutAHermesHomeSkipsProvisioning() {
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.save(null, "New Project", new HermesSetupProfileService.HermesSetupProfileSaveRequest(
                "C:/repos/new-project", List.of("discord"), null, null, null, List.of(),
                null, null, null, null, false, null, null));

        verify(incidentReader, never()).provisionHermesHome(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aBadHermesHomeDoesNotBlockSavingTheRestOfTheProfile() {
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(incidentReader.provisionHermesHome("::not-a-real-path::"))
                .thenThrow(new HermesIncidentReader.HermesIncidentException("boom"));

        HermesSetupProfileView result = service.save(null, "New Project", new HermesSetupProfileService.HermesSetupProfileSaveRequest(
                "C:/repos/new-project", List.of("discord"), null, null, null, List.of(),
                null, null, null, null, false, null, "::not-a-real-path::"));

        assertThat(result.repoPath()).isEqualTo("C:/repos/new-project");
    }
}
