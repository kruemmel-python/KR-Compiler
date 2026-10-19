# OpenCL-Kernel-Inventar
Diese Liste beschreibt die in `src/CC_OpenCL.c` eingebetteten OpenCL-Kernel. Sie ist als Wartungs- und Review-Hilfe gedacht.
| Kernel | Primärer Zweck |
|---|---|
| `abstract_to_symbolic_concepts_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `adam_update` | Adam-Parameterupdate. |
| `add_bias_mn` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `add_broadcast_pe` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `add_elementwise` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `autonomic_cycle_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `brain_bridge_cycle` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `broadcast_add_bias` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `cc_gated_agent_decay` | Enterprise Pack: billiger Sleep-State-/Nährstoff-Decay für inaktive Agenten. |
| `cc_mark_active_agents` | Enterprise Pack: GPU-seitige Aktivitätsmarkierung und Kompaktierung. |
| `cc_morphogenetic_rule_step` | Enterprise Pack: tabellarische morphogenetische Zell-/Regeltransformation. |
| `cc_resonant_field_step` | Enterprise Pack: gekoppelte Feldresonanz über fixed-K Nachbarschaften. |
| `context_resonance_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `conv2d_backward_input` | Convolution Backward Input. |
| `conv2d_backward_weight` | Convolution Backward Weight. |
| `conv2d_bias_grad` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `conv2d_forward` | Convolution Forward Pass. |
| `cross_entropy_loss_grad` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `dream_state_generator_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `dynamic_token_assignment` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `embedding_backward_calc_delta_local` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `embedding_lookup` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `fused_diffusion` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `gelu_backward_elementwise` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `gelu_elementwise` | Elementweises GELU. |
| `generate_system_narrative_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `hebbian_update_local_reduce` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `intuition_precognition_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `ising_metropolis_step` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `izhikevich_neuron_step` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `layer_norm` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `layer_norm_backward` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `lbm_collide_and_stream` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `log_softmax_stable_rowwise` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `matmul_backward_da` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `matmul_backward_db` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `matmul_batched` | Batched Matrixmultiplikation. |
| `matmul_batched_backward_da` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `matmul_batched_backward_db` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `matrix_multiply` | Matrixmultiplikation. |
| `mul_backward` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `mul_elementwise` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `mycel_colony_update` | Kolonie-Zustandsupdate. |
| `mycel_diffuse_decay` | Pheromon-/Mycelium-Diffusion und Zerfall. |
| `mycel_nutrient_update` | Nährstofffeld-Update. |
| `mycel_reinforce` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `nbody_calculate_forces` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `nbody_integrate` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `pairwise_similarity_dot` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `patch_permute_reshape` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `patch_permute_reshape_backward` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `proto_segmented_sum_atomic` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `proto_update_step` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `qualia_resonator_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_apply_controlled_not` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_apply_controlled_phase` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_apply_single_qubit` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_compute_probabilities` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_expectation_pauli_z` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_modular_exponentiation` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_phase_flip_except_zero` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_phase_oracle` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `quantum_swap_qubits` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `reduce_sum_axis01` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `render_debug` | Debug-Rendering. |
| `render_frame_buf` | Framebuffer-Rendering. |
| `render_frame_img` | Image-Rendering. |
| `shape_loss_reward_penalty` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `shape_loss_reward_penalty_list` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `softmax_backward` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `softmax_rowwise` | Zeilenweiser Softmax. |
| `sqse_decrypt` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `sqse_encrypt` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `stdp_update_step` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `stdp_update_traces` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `subqg_inject_agents` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `subqg_simulation_step` | SubQG Simulationsschritt. |
| `threshold_spike` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `transformation_planner_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `transpose` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `transpose_12_batched` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `transpose_backward` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `transpose_batched_last_two` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
| `update_genetic_agents_kernel` | Genetischer Agentenupdate. |
| `vqe_gradient_batch_kernel` | Spezialisierter GPU-Kernel; Details siehe Host-Wrapper und Kernelquelle. |
