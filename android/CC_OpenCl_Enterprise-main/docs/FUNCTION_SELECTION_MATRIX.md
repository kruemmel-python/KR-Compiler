# Function Selection Matrix

| Kategorie | Primäre Funktionen | Hinweis |
|---|---|---|
| lifecycle | initialize_gpu, shutdown_gpu, finish_gpu, cc_get_last_error, cc_get_version, set_kernel_blocking | siehe AGENT_USAGE_GUIDE.md |
| memory | allocate_gpu_memory, free_gpu_memory, write_host_to_gpu_blocking, read_gpu_to_host_blocking | siehe AGENT_USAGE_GUIDE.md |
| mycelium | subqg_init_mycel, subqg_set_active_T, subqg_realloc_pheromone_channels, set_pheromone_gains, set_diffusion_params, set_neighbors_sparse, set_mood_state, set_nutrient_state ... | siehe AGENT_USAGE_GUIDE.md |
| subqg | subqg_initialize_state, subqg_initialize_state_batched, subqg_set_multifield_state, subqg_get_multifield_state, subqg_debug_read_channel, subqg_debug_read_field, subqg_simulation_step, subqg_simulation_step_batched ... | siehe AGENT_USAGE_GUIDE.md |
| enterprise_algorithms | execute_resonant_field_step_gpu, execute_energy_gated_scheduler_gpu, execute_morphogenetic_rule_step_gpu | siehe AGENT_USAGE_GUIDE.md |
| quantum | execute_shor_gpu, execute_grover_gpu, execute_vqe_gpu, execute_vqe_gradients_parallel_gpu, execute_qaoa_gpu, execute_hhl_gpu, execute_qml_classifier_gpu, execute_qec_cycle_gpu ... | siehe AGENT_USAGE_GUIDE.md |
| cognitive | compute_qualia_resonance_gpu, compute_intuition_precognition_gpu, compute_context_resonance_gpu, generate_dream_state_gpu, plan_transformation_gpu, generate_system_narrative_gpu, abstract_to_symbolic_concepts_gpu | siehe AGENT_USAGE_GUIDE.md |
| sqse_noise | execute_sqse_encrypt_float, execute_sqse_decrypt_float, set_noise_level, get_noise_level, register_kernel_measurement_buffers, reset_kernel_measurement_buffers, get_last_kernel_metrics | siehe AGENT_USAGE_GUIDE.md |
| simulated_vram | simulated_kernel_allocate, simulated_kernel_free, simulated_kernel_write, simulated_get_compute_unit_count | siehe AGENT_USAGE_GUIDE.md |

## Vollständige Exportliste

- `set_kernel_blocking`
- `subqg_initialize_state_batched`
- `set_quantum_enabled`
- `cc_get_last_error`
- `get_last_quantum_echo_profile`
- `set_quantum_enabled`
- `initialize_gpu`
- `free_gpu_memory`
- `write_host_to_gpu_blocking`
- `read_gpu_to_host_blocking`
- `subqg_initialize_state`
- `subqg_initialize_state_batched`
- `subqg_debug_read_field`
- `subqg_set_multifield_state`
- `subqg_get_multifield_state`
- `subqg_debug_read_channel`
- `subqg_simulation_step`
- `subqg_simulation_step_batched`
- `subqg_inject_agents`
- `update_genetic_agents`
- `subqg_init_mycel`
- `subqg_set_active_T`
- `subqg_realloc_pheromone_channels`
- `subqg_set_repro_params`
- `subqg_set_nutrient_recovery`
- `set_pheromone_gains`
- `set_diffusion_params`
- `set_neighbors_sparse`
- `set_mood_state`
- `set_nutrient_state`
- `step_pheromone_reinforce`
- `step_pheromone_diffuse_decay`
- `step_mycel_update`
- `step_colony_update`
- `step_reproduction`
- `step_subqg_feedback`
- `step_potential_for_hpio`
- `read_pheromone_slice`
- `subqg_get_dims`
- `read_full_pheromone_buffer`
- `render_frame_to_buffer`
- `read_nutrient`
- `read_potential`
- `read_colonies`
- `save_mycel_state`
- `load_mycel_state`
- `subqg_set_deterministic_mode`
- `subqg_release_state`
- `step_hebbian_social_learning`
- `mycel_agent_cycle`
- `cycle_vram_organism`
- `subqg_set_params`
- `launch_shadow_self_reenqueue`
- `execute_shor_gpu`
- `execute_grover_gpu`
- `execute_vqe_gpu`
- `execute_vqe_gradients_parallel_gpu`
- `compute_qualia_resonance_gpu`
- `compute_intuition_precognition_gpu`
- `compute_context_resonance_gpu`
- `generate_dream_state_gpu`
- `plan_transformation_gpu`
- `generate_system_narrative_gpu`
- `abstract_to_symbolic_concepts_gpu`
- `execute_qaoa_gpu`
- `execute_hhl_gpu`
- `execute_qml_classifier_gpu`
- `execute_qec_cycle_gpu`
- `quantum_upload_gate_sequence`
- `quantum_apply_gate_sequence`
- `quantum_export_to_qasm`
- `quantum_import_from_qasm`
- `execute_quantum_echoes_otoc_gpu`
- `shutdown_gpu`
- `finish_gpu`
- `cc_get_last_error`
- `cc_get_version`
- `execute_resonant_field_step_gpu`
- `execute_energy_gated_scheduler_gpu`
- `execute_morphogenetic_rule_step_gpu`
- `execute_matmul_on_gpu`
- `execute_softmax_on_gpu`
- `execute_gelu_on_gpu`
- `execute_add_on_gpu`
- `execute_add_bias_on_gpu`
- `execute_mul_on_gpu`
- `execute_conv2d_forward_on_gpu`
- `execute_conv2d_backward_on_gpu`
- `execute_patch_permute_reshape_on_gpu`
- `execute_patch_permute_reshape_backward_on_gpu`
- `execute_eon_encoder_chain_on_gpu`
- `execute_eon_encoder_backward_chain_on_gpu`
- `compute_ctc_loss_cpu`
- `execute_layernorm_on_gpu`
- `execute_clone_on_gpu`
- `execute_transpose_on_gpu`
- `execute_gelu_backward_on_gpu`
- `execute_matmul_backward_on_gpu`
- `execute_layernorm_backward_on_gpu`
- `execute_adam_update_on_gpu`
- `execute_softmax_backward_on_gpu`
- `execute_mul_backward_on_gpu`
- `execute_transpose_backward_on_gpu`
- `execute_embedding_lookup_gpu`
- `execute_embedding_backward_gpu`
- `execute_reduce_sum_gpu`
- `execute_broadcast_add_gpu`
- `execute_transpose_batched_gpu`
- `execute_transpose_12_batched_gpu`
- `execute_matmul_batched_on_gpu`
- `execute_matmul_batched_backward_on_gpu`
- `execute_log_softmax_stable_gpu`
- `execute_cross_entropy_loss_grad_gpu`
- `execute_add_broadcast_pe_gpu`
- `execute_hebbian_update_on_gpu`
- `execute_threshold_spike_on_gpu`
- `execute_dynamic_token_assignment_gpu`
- `execute_pairwise_similarity_gpu`
- `execute_proto_segmented_sum_gpu`
- `execute_proto_update_step_gpu`
- `execute_shape_loss_with_reward_penalty_gpu`
- `execute_fused_diffusion_on_gpu`
- `execute_izhikevich_step_on_gpu`
- `execute_stdp_update_on_gpu`
- `execute_stdp_trace_update_on_gpu`
- `execute_lbm_collide_and_stream_on_gpu`
- `execute_nbody_calculate_forces_on_gpu`
- `execute_nbody_integrate_on_gpu`
- `execute_ising_metropolis_step_on_gpu`
- `execute_shape_loss_with_reward_penalty_list_gpu`
- `sqse_load_kernels`
- `execute_sqse_encrypt_float`
- `execute_sqse_decrypt_float`
- `set_noise_level`
- `get_noise_level`
- `register_kernel_measurement_buffers`
- `reset_kernel_measurement_buffers`
- `get_last_kernel_metrics`
- `simulated_kernel_allocate`
- `simulated_kernel_free`
- `simulated_kernel_write`
- `simulated_get_compute_unit_count`