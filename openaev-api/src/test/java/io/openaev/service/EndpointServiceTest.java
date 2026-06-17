package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

  private static final String TENANT_ID = "tenant-test-id";

  @Mock private EndpointRepository endpointRepository;
  @Mock private TagRepository tagRepository;
  @Mock private AgentService agentService;
  @Mock private AssetService assetService;

  @InjectMocks private EndpointService endpointService;

  @Nested
  @DisplayName("syncAgentsEndpoints - source tag")
  class SyncAgentsEndpointsSourceTag {

    private Executor createExecutor(String name, String type) {
      Executor executor = new Executor();
      executor.setName(name);
      executor.setType(type);
      executor.setBackgroundColor("#FF0000");
      return executor;
    }

    private AgentRegisterInput createAgentRegisterInput(Executor executor, String externalRef) {
      AgentRegisterInput input = new AgentRegisterInput();
      input.setExecutor(executor);
      input.setExternalReference(externalRef);
      input.setName("test-host");
      input.setHostname("test-host");
      input.setIps(new String[] {"10.0.0.1"});
      input.setMacAddresses(new String[] {"AA:BB:CC:DD:EE:FF"});
      input.setSeenIp("10.0.0.1");
      input.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
      input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
      input.setElevated(true);
      input.setService(true);
      input.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);
      input.setLastSeen(Instant.now());
      return input;
    }

    @Test
    @DisplayName("given new CrowdStrike endpoint should add source:crowdstrike tag")
    void given_newCrowdStrikeEndpoint_should_addSourceTag() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-001");

      Tag sourceTag = new Tag();
      sourceTag.setName("source:crowdstrike");
      sourceTag.setColor("#FF0000");

      when(tagRepository.findByName("source:crowdstrike")).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(sourceTag);
      when(endpointRepository.findByAtleastOneMacAddress(any(), eq(TENANT_ID)))
          .thenReturn(List.of());
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(new ArrayList<>(List.of(input)), List.of(), TENANT_ID);

      // Assert
      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());

      List<Asset> saved = savedEndpoints.getValue();
      assertThat(saved).hasSize(1);
      Endpoint savedEndpoint = (Endpoint) saved.getFirst();
      assertThat(savedEndpoint.getTags()).extracting(Tag::getName).contains("source:crowdstrike");
    }

    @Test
    @DisplayName("given existing CrowdStrike endpoint should add source:crowdstrike tag")
    void given_existingCrowdStrikeEndpoint_should_addSourceTag() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-001");

      Endpoint existingEndpoint = EndpointFixture.createEndpoint();
      existingEndpoint.setTags(new HashSet<>());

      Agent existingAgent = AgentFixture.createAgent(existingEndpoint, "cs-device-001");
      existingAgent.setExecutor(csExecutor);

      Tag sourceTag = new Tag();
      sourceTag.setName("source:crowdstrike");
      sourceTag.setColor("#FF0000");

      when(tagRepository.findByName("source:crowdstrike")).thenReturn(Optional.of(sourceTag));
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(
          new ArrayList<>(List.of(input)), List.of(existingAgent), TENANT_ID);

      // Assert
      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());

      Endpoint savedEndpoint = (Endpoint) savedEndpoints.getValue().getFirst();
      assertThat(savedEndpoint.getTags()).extracting(Tag::getName).contains("source:crowdstrike");
    }

    @Test
    @DisplayName("given Tanium endpoint should add source:tanium tag")
    void given_taniumEndpoint_should_addSourceTag() {
      // Arrange
      Executor taniumExecutor = createExecutor("Tanium", "openaev_tanium");
      AgentRegisterInput input = createAgentRegisterInput(taniumExecutor, "tanium-device-001");

      Tag sourceTag = new Tag();
      sourceTag.setName("source:tanium");

      when(tagRepository.findByName("source:tanium")).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class)))
          .thenAnswer(
              inv -> {
                Tag saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID().toString());
                return saved;
              });
      when(endpointRepository.findByAtleastOneMacAddress(any(), eq(TENANT_ID)))
          .thenReturn(List.of());
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(new ArrayList<>(List.of(input)), List.of(), TENANT_ID);

      // Assert
      ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
      verify(tagRepository).save(tagCaptor.capture());
      assertThat(tagCaptor.getValue().getName()).isEqualTo("source:tanium");

      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());
      Endpoint savedEndpoint = (Endpoint) savedEndpoints.getValue().getFirst();
      assertThat(savedEndpoint.getTags()).extracting(Tag::getName).contains("source:tanium");
    }

    @Test
    @DisplayName("given existing source tag should not create duplicate")
    void given_existingSourceTag_should_notCreateDuplicate() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-002");

      Tag existingTag = new Tag();
      existingTag.setId(UUID.randomUUID().toString());
      existingTag.setName("source:crowdstrike");
      existingTag.setColor("#FF0000");

      when(tagRepository.findByName("source:crowdstrike")).thenReturn(Optional.of(existingTag));
      when(endpointRepository.findByAtleastOneMacAddress(any(), eq(TENANT_ID)))
          .thenReturn(List.of());
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(new ArrayList<>(List.of(input)), List.of(), TENANT_ID);

      // Assert
      verify(tagRepository, never()).save(any(Tag.class));

      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());
      Endpoint savedEndpoint = (Endpoint) savedEndpoints.getValue().getFirst();
      assertThat(savedEndpoint.getTags()).contains(existingTag);
    }

    @Test
    @DisplayName(
        "given endpoint with another executor source tag should add new tag without removing existing")
    void given_endpointWithOtherExecutorSourceTag_should_addNewTagAndPreserveExisting() {
      // Arrange
      Executor csExecutor = createExecutor("CrowdStrike", "openaev_crowdstrike");
      AgentRegisterInput input = createAgentRegisterInput(csExecutor, "cs-device-003");

      Endpoint existingEndpoint = EndpointFixture.createEndpoint();
      Tag otherExecutorTag = new Tag();
      otherExecutorTag.setName("source:tanium");
      existingEndpoint.setTags(new HashSet<>(Set.of(otherExecutorTag)));

      Agent existingAgent = AgentFixture.createAgent(existingEndpoint, "cs-device-003");
      existingAgent.setExecutor(csExecutor);

      Tag csTag = new Tag();
      csTag.setName("source:crowdstrike");
      when(tagRepository.findByName("source:crowdstrike")).thenReturn(Optional.of(csTag));
      when(agentService.saveAllAgents(any())).thenAnswer(inv -> inv.getArgument(0));

      // Act
      endpointService.syncAgentsEndpoints(
          new ArrayList<>(List.of(input)), List.of(existingAgent), TENANT_ID);

      // Assert — both source tags must be present (endpoint has multiple active executors)
      ArgumentCaptor<List<Asset>> savedEndpoints = ArgumentCaptor.forClass(List.class);
      verify(assetService).saveAllAssets(savedEndpoints.capture());
      Endpoint savedEndpoint = (Endpoint) savedEndpoints.getValue().getFirst();
      assertThat(savedEndpoint.getTags())
          .extracting(Tag::getName)
          .contains("source:crowdstrike", "source:tanium");
    }
  }
}
