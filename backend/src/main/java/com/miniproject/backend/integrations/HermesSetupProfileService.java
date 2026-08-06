package com.miniproject.backend.integrations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Plain CRUD, not an AI skill -- same shape as ProjectWorkspaceService /
 * HermesStatusService, not routed through CoordinatorService/Artifact.
 * Saving is an explicit, separate action from generating the YAML (Setup
 * Wizard skill run) so a "just generate once" use doesn't force a DB write.
 */
@Service
public class HermesSetupProfileService {

    private final HermesSetupProfileRepository repository;
    private final HermesIncidentReader incidentReader;

    public HermesSetupProfileService(HermesSetupProfileRepository repository, HermesIncidentReader incidentReader) {
        this.repository = repository;
        this.incidentReader = incidentReader;
    }

    @Transactional
    public HermesSetupProfileView save(String id, String name, HermesSetupProfileSaveRequest request) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        HermesSetupProfileEntity entity = (id == null || id.isBlank())
                ? new HermesSetupProfileEntity(null, name.trim())
                : repository.findById(id).orElse(new HermesSetupProfileEntity(id, name.trim()));
        entity.applyAnswers(
                request.repoPath().trim(),
                request.platforms(),
                request.discordChannelId(),
                request.emailImapHost(),
                request.emailAccount(),
                request.emailAllowedSenders(),
                request.incidentReportsDir(),
                request.incidentExtractsDir(),
                request.incidentDownloadsDir(),
                request.serverLogPath(),
                request.prPackageEnabled() != null && request.prPackageEnabled(),
                request.gitHost(),
                request.hermesHome());

        // A hermes_home for a brand-new project won't have an incidents/
        // agent-tasks skeleton yet -- create it as part of saving instead of
        // making the analyst do it as a separate step first. Best-effort:
        // a bad path here shouldn't block saving the rest of the profile.
        if (request.hermesHome() != null && !request.hermesHome().isBlank()) {
            try {
                incidentReader.provisionHermesHome(request.hermesHome());
            } catch (RuntimeException ignored) {
                // best-effort -- analyst can still create it manually via the wizard's notice
            }
        }

        return repository.save(entity).toView();
    }

    @Transactional(readOnly = true)
    public List<HermesSetupProfileView> listAll() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(HermesSetupProfileEntity::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<HermesSetupProfileView> get(String id) {
        return repository.findById(id).map(HermesSetupProfileEntity::toView);
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("No setup profile found for id " + id);
        }
        repository.deleteById(id);
    }

    public record HermesSetupProfileSaveRequest(
            String repoPath,
            List<String> platforms,
            String discordChannelId,
            String emailImapHost,
            String emailAccount,
            List<String> emailAllowedSenders,
            String incidentReportsDir,
            String incidentExtractsDir,
            String incidentDownloadsDir,
            String serverLogPath,
            Boolean prPackageEnabled,
            String gitHost,
            String hermesHome) {
    }
}
