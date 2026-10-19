# API-Referenz: CC_OpenCl.dll

Diese Referenz wurde aus `src/CC_OpenCL.c` und `include/SymBio_Interface.h` abgeleitet. `void*`-Bufferparameter sind in der Regel OpenCL-`cl_mem`-Handles, die bewusst als `void*` durch die DLL-ABI geführt werden.

## Runtime / Speicher / Diagnose

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 1007 | `void` | `set_kernel_blocking` | `int blocking_enabled` |

| 2256 | `void` | `set_quantum_enabled` | `int enabled); DLLEXPORT int initialize_gpu(int gpu_index); DLLEXPORT void *allocate_gpu_memory(int gpu_index, size_t size); DLLEXPORT void free_gpu_memory(int gpu_index, void* buffer_handle); DLLEXPORT int write_host_to_gpu_blocking(int gpu_index, void* gpu_buffer_handle, size_t offset, size_t size, const void* host_source_ptr); DLLEXPORT int read_gpu_to_host_blocking(int gpu_index, void* gpu_buffer_handle, size_t offset, size_t size, void* host_destination_ptr); DLLEXPORT unsigned int simulated_get_compute_unit_count(int gpu_index); // Kept for dummy mode DLLEXPORT void shutdown_gpu(int gpu_index); DLLEXPORT int finish_gpu(int gpu_index); // Kernel Execution Function Exports DLLEXPORT int execute_matmul_on_gpu(int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int B, int M, int N, int K); DLLEXPORT int execute_softmax_on_gpu(int gpu_index, void* buffer_input, void* buffer_output, int num_rows, int row_size); DLLEXPORT int execute_gelu_on_gpu(int gpu_index, void* buffer_input, void* buffer_output, int num_elements); DLLEXPORT int execute_add_on_gpu(int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int num_elements); DLLEXPORT int execute_add_bias_on_gpu(int gpu_index, void* buffer_a_or_c, void* buffer_b_bias, int M, int N); DLLEXPORT int execute_mul_on_gpu(int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int num_elements); DLLEXPORT int execute_layernorm_on_gpu(int gpu_index, void* buffer_input, void* buffer_output, int num_rows, int row_size, float eps); DLLEXPORT int execute_clone_on_gpu(int gpu_index, void* src_buffer, void* dst_buffer, size_t size); DLLEXPORT int execute_transpose_on_gpu(int gpu_index, void* buffer_input, void* buffer_output, int rows, int cols); DLLEXPORT int execute_gelu_backward_on_gpu(int gpu_index, void* buffer_input, void* buffer_grad_output, void* buffer_grad_input, int num_elements); DLLEXPORT int execute_matmul_backward_on_gpu(int gpu_index, void* buffer_a, void* buffer_b, void* buffer_dc, void* buffer_da, void* buffer_db, int B, int M, int N, int K); DLLEXPORT int execute_layernorm_backward_on_gpu(int gpu_index, void* buffer_dy, void* buffer_x, void* buffer_dx, int num_rows, int row_size, float eps); DLLEXPORT int execute_adam_update_on_gpu(int gpu_index, void* param_buffer, void* grad_buffer, void* m_buffer, void* v_buffer, int num_elements, int t, float lr, float beta1, float beta2, float eps, float weight_decay); DLLEXPORT int execute_softmax_backward_on_gpu(int gpu_index, void* buffer_dy, void* buffer_y, void* buffer_dx, int num_rows, int row_size); DLLEXPORT int execute_mul_backward_on_gpu(int gpu_index, void* buffer_dC, void* buffer_A, void* buffer_B, void* buffer_dA, void* buffer_dB, int num_elements); DLLEXPORT int execute_transpose_backward_on_gpu(int gpu_index, void* buffer_dC, void* buffer_dA, int rows_A, int cols_A); DLLEXPORT int execute_embedding_lookup_gpu(int gpu_index, void* idx, void* w, void* o, int b, int s, int d, int v); DLLEXPORT int execute_embedding_backward_gpu(int gpu_index, void* d_o, void* idx, void* d_w, int b, int s, int d, int v); DLLEXPORT int execute_reduce_sum_gpu(int gpu_index, void* in, void* out, int B, int M, int N); DLLEXPORT int execute_broadcast_add_gpu(int gpu_index, void* a, void* b, void* c, int B, int M, int N); DLLEXPORT int execute_transpose_batched_gpu(int gpu_index, void* in, void* out, int B_flat, int d1, int d2); DLLEXPORT int execute_transpose_12_batched_gpu(int gpu_index, void* buffer_in, void* buffer_out, int B, int D1, int D2, int D3); DLLEXPORT int execute_matmul_batched_on_gpu(int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int B, int M, int N, int K); DLLEXPORT int execute_matmul_batched_backward_on_gpu(int gpu_index, void* buffer_a, void* buffer_b, void* buffer_dc, void* buffer_da, void* buffer_db, int B, int M, int N, int K); DLLEXPORT int execute_log_softmax_stable_gpu(int gpu_index, void* input_logits, void* output_log_probs, int B_S_rows, int V_cols); DLLEXPORT int execute_cross_entropy_loss_grad_gpu(int gpu_index, void* log_probs, void* target_indices, void* grad_input, void* loss_per_sample, int num_rows, int V); DLLEXPORT int execute_add_broadcast_pe_gpu(int gpu_index, void* input, void* pe_slice, void* output, int B, int S, int E); DLLEXPORT int execute_hebbian_update_on_gpu(int gpu_index, void* buffer_a, void* buffer_c, void* buffer_w, float learning_rate, int B, int M, int N, int K); DLLEXPORT int execute_threshold_spike_on_gpu(int gpu_index, void* buffer_activations, void* buffer_spikes, float threshold, int num_elements); DLLEXPORT int execute_dynamic_token_assignment_gpu(int gpu_index, void* activations_bse, void* prototypes_te, void* output_indices_bs, int B, int S, int E, int T); DLLEXPORT int execute_pairwise_similarity_gpu(int gpu_index, void* states_nd, void* output_similarity_nn, int N, int D); DLLEXPORT int execute_proto_segmented_sum_gpu(int gpu_index, void* activations_flat, void* indices_flat, void* proto_sums, void* proto_counts, int num_elements_flat, int E, int T); DLLEXPORT int execute_proto_update_step_gpu(int gpu_index, void* prototypes, void* proto_sums, void* proto_counts, float learning_rate, int E, int T); // Loss Shaping Exports DLLEXPORT int execute_shape_loss_with_reward_penalty_gpu(int gpu_index, void* loss_per_sample_in, void* predictions, void* targets, void* loss_per_sample_out, int num_samples, int num_classes, float penalty_weight, float reward_weight, float high_confidence_threshold, int critical_target_class, int critical_predicted_class); DLLEXPORT int execute_shape_loss_with_reward_penalty_list_gpu(int gpu_index, void* loss_per_sample_in, void* predictions, void* targets, void* loss_per_sample_out, void* critical_pairs, int num_samples, int num_classes, int num_critical_pairs, float penalty_weight, float reward_weight, float high_confidence_threshold); // NEU DLLEXPORT int execute_fused_diffusion_on_gpu(int gpu_index, void* buffer_X, void* buffer_W, void* buffer_O, int B, int N, int D, float gamma, float sigma); DLLEXPORT int execute_izhikevich_step_on_gpu(int gpu_index, void* v, void* u, void* i_inj, void* spikes_out, void* p_a, void* p_b, void* p_c, void* p_d, float dt, float threshold, int num_neurons); DLLEXPORT int mycel_agent_cycle(int gpu_index, int cycles, float sensory_gain, float learning_rate, float time_step); DLLEXPORT int cycle_vram_organism(int gpu_index, int cycles, float sensory_gain, float learning_rate); DLLEXPORT int execute_stdp_update_on_gpu(int gpu_index, void* weights, void* pre_traces, void* post_traces, void* pre_spike_events, void* post_spike_events, float lr_ltp, float lr_ltd, int pre_n, int post_n); DLLEXPORT int execute_stdp_trace_update_on_gpu(int gpu_index, void* pre_traces, void* post_traces, void* pre_spike_events, void* post_spike_events, float decay_pre, float decay_post, float increment_pre, float increment_post, int pre_n, int post_n); DLLEXPORT int execute_lbm_collide_and_stream_on_gpu(int gpu_index, void* f_in, void* f_out, void* rho, void* ux, void* uy, float omega, int width, int height); DLLEXPORT int execute_nbody_calculate_forces_on_gpu(int gpu_index, void* positions, void* forces, float gravitational_const, float softening_factor, int num_bodies); DLLEXPORT int execute_nbody_integrate_on_gpu(int gpu_index, void* positions, void* velocities, void* forces, float dt, int num_bodies); DLLEXPORT int execute_ising_metropolis_step_on_gpu(int gpu_index, void* spin_grid, void* random_numbers, float J, float beta, int width, int height, int color); DLLEXPORT int sqse_load_kernels(const char* kernel_path); DLLEXPORT int execute_sqse_encrypt_float(const float* data_in, const float* key, int n, float chaos_K, int steps, float* out_theta, float* out_p_masked); DLLEXPORT int execute_sqse_decrypt_float(const float* in_theta, const float* in_p_masked, const float* key, int n, float chaos_K, int steps, float* data_out); DLLEXPORT void set_noise_level(int gpu_index, float value); DLLEXPORT float get_noise_level(int gpu_index); DLLEXPORT void register_kernel_measurement_buffers(float* error_ptr, float* variance_ptr); DLLEXPORT void reset_kernel_measurement_buffers(void); DLLEXPORT int get_last_kernel_metrics(int gpu_index, KernelMetricsSample* out_metrics); DLLEXPORT int subqg_initialize_state(int gpu_index, float initial_energy, float initial_phase, float noise_level, float threshold); DLLEXPORT int subqg_initialize_state_batched(int gpu_index, int cell_count, const float* initial_energy, const float* initial_phase, float noise_level, float threshold); DLLEXPORT int subqg_simulation_step(int gpu_index, float rng_energy, float rng_phase, float rng_spin, float* out_energy, float* out_phase, float* out_interference, int* out_node_flag, int* out_spin, int* out_topology, float* out_field_map, int field_map_length); DLLEXPORT int subqg_simulation_step_batched(int gpu_index, const float* rng_energy, const float* rng_phase, const float* rng_spin, int batch_count, float* out_energy, float* out_phase, float* out_interference, int* out_node_flag, int* out_spin, int* out_topology, float* out_field_map, int field_map_length); DLLEXPORT void subqg_set_deterministic_mode(int enabled, uint64_t seed); DLLEXPORT void subqg_release_state(int gpu_index); DLLEXPORT int subqg_inject_agents(int gpu_index, const HPIOAgent* agents, int count); DLLEXPORT int update_genetic_agents(int gpu_index, const float* agent_states_in, float* agent_states_out, int agent_state_stride, int agent_count, float time_step); DLLEXPORT int step_hebbian_social_learning(int gpu_index, float learning_rate); DLLEXPORT void subqg_set_params(float noise_level, float threshold); DLLEXPORT int launch_shadow_self_reenqueue(int gpu_index, int work_items, int max_generations); // Mycel / pheromone hybrid exports #ifdef __cplusplus extern "C" { #endif DLLEXPORT int subqg_init_mycel(int gpu_index, int T_cap, int C, int K); DLLEXPORT int subqg_set_active_T(int gpu_index, int T_act); /* Size-probe fähig: out==NULL oder bytes==0 → gibt nur die benötigten Bytes zurück. */ DLLEXPORT int read_full_pheromone_buffer(void* out_buffer, int out_bytes); #ifdef __cplusplus } /* extern "C" */ #endif DLLEXPORT int subqg_realloc_pheromone_channels(int gpu_index, int new_C); DLLEXPORT int subqg_set_repro_params(int gpu_index, float thr_nu, float thr_act, float mut_sigma); DLLEXPORT int subqg_set_nutrient_recovery(int gpu_index, float recovery_rate); DLLEXPORT int set_pheromone_gains(int gpu_index, const float* gain_C, int count); DLLEXPORT int set_diffusion_params(int gpu_index, float decay_default, float diffu_default); DLLEXPORT int set_neighbors_sparse(int gpu_index, const int* neigh_idx_TK); DLLEXPORT int set_mood_state(int gpu_index, const float* mood_tC); DLLEXPORT int set_nutrient_state(int gpu_index, const float* nutrient_t); DLLEXPORT int step_pheromone_reinforce(int gpu_index, const float* activity_t); DLLEXPORT int step_pheromone_diffuse_decay(int gpu_index); DLLEXPORT int step_mycel_update(int gpu_index, const float* activity_t); DLLEXPORT int step_colony_update(int gpu_index, int iterations); DLLEXPORT int step_reproduction(int gpu_index, const float* activity_t, const float* prototypes, int E); DLLEXPORT int step_subqg_feedback(int gpu_index, float kappa_nutrient, const float* kappa_mood, int count); DLLEXPORT int step_potential_for_hpio(int gpu_index, const float* mood_weights, int count); DLLEXPORT int read_pheromone_slice(int gpu_index, int channel, float* out_TK); DLLEXPORT int render_frame_to_buffer(int gpu_index, int width, int height, void* out_buffer_host, const RenderAgent* agents, int num_agents, const Vec2f* trail_points, int num_trail_points, float exposure_scale, float agent_radius, float trail_thickness, float clip_percentile); DLLEXPORT int read_nutrient(int gpu_index, float* out_T); DLLEXPORT int read_potential(int gpu_index, float* out_T); DLLEXPORT int read_colonies(int gpu_index, uint8_t* out_T); DLLEXPORT int save_mycel_state(int gpu_index, const char* path); DLLEXPORT int load_mycel_state(int gpu_index, const char* path); // Quantum algorithm support structures and exports typedef struct { uint64_t z_mask; float coefficient; } PauliZTerm; DLLEXPORT int execute_shor_gpu(int gpu_index, int modulus_N, int base_a, int* out_period_estimate, float* out_control_distribution, int distribution_length); DLLEXPORT int execute_grover_gpu(int gpu_index, int num_qubits, int iterations, uint64_t marked_mask, uint64_t marked_value, int* out_marked_state, float* out_distribution, int distribution_length); DLLEXPORT int execute_vqe_gpu(int gpu_index, int num_qubits, int ansatz_layers, const float* parameters, int num_parameters, const PauliZTerm* hamiltonian_terms, int num_terms, float* out_energy, float* out_gradients); DLLEXPORT int execute_vqe_gradients_parallel_gpu(int gpu_index, int num_qubits, int ansatz_layers, const float* parameters, int num_parameters, const PauliZTerm* hamiltonian_terms, int num_terms, float* out_energy, float* out_gradients); DLLEXPORT int execute_qaoa_gpu(int gpu_index, int num_qubits, int p_layers, const float* gammas, const float* betas, int num_parameters, const PauliZTerm* cost_terms, int num_cost_terms, float* out_energy); DLLEXPORT int execute_hhl_gpu(int gpu_index, const float* matrix_A, const float* vector_b, int system_size, float* out_solution, int solution_length); DLLEXPORT int execute_qml_classifier_gpu(int gpu_index, int num_qubits, const float* feature_vector, int num_features, const float* parameters, int num_parameters, float* out_expectations, int expectation_length); DLLEXPORT int execute_qec_cycle_gpu(int gpu_index, int code_type, uint32_t error_mask, float* out_syndrome, int syndrome_length); DLLEXPORT int quantum_upload_gate_sequence(int gpu_index, const QuantumGate* gates, int gate_count); DLLEXPORT int quantum_apply_gate_sequence(int gpu_index, int num_qubits, float* out_probabilities, int probability_length); DLLEXPORT int quantum_export_to_qasm(int gpu_index, const char* filepath); /** * @brief Execute the GPU-based quantum echo / OTOC(2) protocol. * * The function initializes the |0…0⟩ state, applies the forward unitary sequence U, * introduces a local perturbation W, and evolves back with U† to estimate the Loschmidt * echo L. Optionally, it evaluates the second-order out-of-time-ordered correlator by * executing the extended sequence involving an additional observer gate V. * * @note The @p gpu_index parameter participates in the emerging multi-device manager. * Commands still execute on the shared kernel set, but the function resolves the * command queue via the slot table when available and otherwise falls back to the * global queue/context until full per-device compilation is introduced. * * @param gpu_index Target GPU index (reserved, see note). * @param num_qubits Number of qubits for the simulated register. * @param U_gates Pointer to the forward evolution gate list U (may be NULL when * @p U_gate_count is zero). * @param U_gate_count Number of entries contained in @p U_gates. * @param W_gate Pointer to the perturbation gate descriptor W (must not be NULL). * @param V_gate Optional observer gate descriptor V (can be NULL when unused). * @param measure_otoc2 Flag selecting whether to evaluate the OTOC(2) branch. * @param out_L Output pointer receiving |⟨0…0|ψ_final⟩|² after U†WU. * @param out_otoc2_real Output pointer for the real part of OTOC(2) (required when * @p measure_otoc2 is non-zero). * @param out_otoc2_imag Output pointer for the imaginary part of OTOC(2) (required when * @p measure_otoc2 is non-zero). * * @return 1 on success, 0 on failure. On error, diagnostic messages are written to stderr * and the OpenCL command queue is drained via finish_queue_and_check(). */ DLLEXPORT int execute_quantum_echoes_otoc_gpu( int gpu_index, int num_qubits, const QuantumGate* U_gates, int U_gate_count, const QuantumGate* W_gate, const QuantumGate* V_gate, int measure_otoc2, float* out_L, float* out_otoc2_real, float* out_otoc2_imag); /* ------------------------------------------------------------------------- * CC Enterprise Algorithm Pack * * Purpose: * Isolate new research-grade GPU algorithms from the historical monolithic * kernel string. The pack is intentionally compiled as one OpenCL program * and exposes multiple kernels so future algorithm families can be added * without destabilising the existing tensor, SubQG, mycelium and quantum * code paths. * ------------------------------------------------------------------------- */ static const char* cc_enterprise_algorithm_pack_src = R"CLC( __kernel void cc_resonant_field_step( __global float* state, __global float* velocity, __global const float* drive, __global float* energy, __global const int* neighbors, __global const float* weights, const int N, const int K, const float dt, const float damping, const float coupling, const float inertia, const float clamp_abs` |

| 2668 | `const char*` | `cc_get_last_error` | `void); DLLEXPORT const char* cc_get_version(void); DLLEXPORT int execute_resonant_field_step_gpu( int gpu_index, void* state_buf, void* velocity_buf, void* drive_buf, void* energy_buf, void* neighbors_buf, void* weights_buf, int N, int K, float dt, float damping, float coupling, float inertia, float clamp_abs); DLLEXPORT int execute_energy_gated_scheduler_gpu( int gpu_index, void* energy_buf, void* nutrient_buf, void* active_flags_buf, void* active_indices_buf, void* active_count_buf, int N, float threshold, float sleep_decay, float nutrient_recovery); DLLEXPORT int execute_morphogenetic_rule_step_gpu( int gpu_index, void* cell_type_buf, void* nutrient_buf, void* energy_buf, void* potential_buf, void* rule_in_type_buf, void* rule_min_nutrient_buf, void* rule_min_energy_buf, void* rule_out_type_buf, void* rule_delta_potential_buf, int N, int R, float nutrient_cost); static int ensure_enterprise_algorithm_pack_ready(int gpu_index); static void release_enterprise_algorithm_pack(void); #define ENQUEUE_KERNEL_PROFILED(kernel_handle, work_dim, global_ptr, local_ptr, kernel_label) \ enqueue_kernel_with_metrics(kernel_handle, work_dim, global_ptr, local_ptr, kernel_label, NULL, NULL) // Quantum helper declarations typedef struct { cl_mem buffer; int num_qubits; size_t dimension; } QuantumStateGPU; typedef struct QuantumEchoProfile { uint64_t single_qubit_gate_count; uint64_t two_qubit_gate_count; uint64_t three_qubit_gate_count; uint64_t fused_single_gate_groups; uint64_t total_gate_applications; uint64_t estimated_global_mem_bytes; uint64_t kernel_enqueue_count; double host_wall_time_ms; int used_out_of_order_queue; } QuantumEchoProfile; static QuantumEchoProfile g_last_quantum_echo_profile = {0}; static QuantumEchoProfile* g_active_quantum_profile = NULL; DLLEXPORT int get_last_quantum_echo_profile(QuantumEchoProfile* out_profile); static int ensure_sqse_kernels_ready(void); static int ensure_brain_kernels(void); static cl_float2 make_complex(float real, float imag); static int ensure_quantum_kernels_ready(void); static int quantum_allocate_state(int num_qubits, QuantumStateGPU* state_out); static void quantum_release_state(QuantumStateGPU* state); static int quantum_initialize_zero_state(QuantumStateGPU* state); static int quantum_apply_single_qubit_gate(QuantumStateGPU* state, int target, cl_float2 g00, cl_float2 g01, cl_float2 g10, cl_float2 g11); static int quantum_apply_hadamard(QuantumStateGPU* state, int target); static int quantum_apply_pauli_x(QuantumStateGPU* state, int target); static int quantum_apply_rotation_x(QuantumStateGPU* state, int target, float theta); static int quantum_apply_rotation_z(QuantumStateGPU* state, int target, float theta); static int quantum_apply_rotation_y(QuantumStateGPU* state, int target, float theta); static int quantum_apply_pauli_z(QuantumStateGPU* state, int target); static int quantum_apply_pauli_y(QuantumStateGPU* state, int target); static int quantum_apply_controlled_phase(QuantumStateGPU* state, int control, int target, float theta); static int quantum_apply_controlled_not(QuantumStateGPU* state, int control, int target); static int quantum_swap_qubits_out_of_place(QuantumStateGPU* state, int q1, int q2); static int quantum_inverse_qft(QuantumStateGPU* state, int start_qubit, int count); static int quantum_apply_modular_exponentiation(QuantumStateGPU* state, int num_control, int num_work, int base_a, int modulus_N); static int quantum_prepare_uniform_superposition(QuantumStateGPU* state, int num_qubits_to_prepare, int start_qubit); static int quantum_apply_grover_oracle(QuantumStateGPU* state, uint64_t mask, uint64_t value); static int quantum_apply_grover_diffusion(QuantumStateGPU* state); static int quantum_compute_probabilities_gpu(QuantumStateGPU* state, cl_mem* probs_out); static int quantum_expectation_pauli_z_gpu(QuantumStateGPU* state, uint64_t z_mask, float* out_value); static int quantum_measure_most_probable(QuantumStateGPU* state, int* out_index); static int quantum_prepare_feature_map(QuantumStateGPU* state, const float* feature_vector, int num_features); static int quantum_apply_qml_classifier_layer(QuantumStateGPU* state, const float* parameters, int num_qubits); static uint32_t round_up_to_power_of_two(uint32_t value); static int quantum_reserve_temp_state(size_t dimension); static int quantum_reserve_probability_buffer(size_t dimension); static uint64_t host_modexp_uint64(uint64_t base, uint64_t exp, uint64_t mod); static int quantum_apply_vqe_ansatz(QuantumStateGPU* state, int num_qubits, int ansatz_layers, const float* parameters, int num_parameters); static int quantum_compute_pauli_z_energy(QuantumStateGPU* state, const PauliZTerm* terms, int num_terms, float* out_energy); static int quantum_apply_multi_qubit_z_phase(QuantumStateGPU* state, uint64_t mask, float angle); static int solve_linear_system(const float* matrix, const float* vector, int n, float* solution); static int quantum_initialize_basis_superposition(QuantumStateGPU* state, const uint32_t* basis_states, size_t count); static int quantum_prepare_steane_zero_state(QuantumStateGPU* state); static int quantum_measure_x_parity_gpu(QuantumStateGPU* state, const int* qubits, int count, float* out_value); static int quantum_apply_gate_cpu(cl_float2* state, int num_qubits, const QuantumGate* gate); static int quantum_apply_gate_from_desc(QuantumStateGPU* state, const QuantumGate* gate); static int quantum_apply_sequence(QuantumStateGPU* state, const QuantumGate* seq, int count); static int quantum_apply_sequence_dagger(QuantumStateGPU* state, const QuantumGate* seq, int count); static int quantum_apply_gate_dagger(QuantumStateGPU* state, const QuantumGate* gate); static int quantum_apply_swap_via_cnot(QuantumStateGPU* state, int q1, int q2); static int quantum_apply_toffoli_decomposed(QuantumStateGPU* state, int control1, int control2, int target); static int quantum_apply_controlled_rz_decomposed(QuantumStateGPU* state, int control, int target, float theta); static int quantum_apply_controlled_rx_decomposed(QuantumStateGPU* state, int control, int target, float theta); static int quantum_apply_controlled_ry_decomposed(QuantumStateGPU* state, int control, int target, float theta); #ifndef NDEBUG static int quantum_check_norm1(int gpu_index, QuantumStateGPU* state, float eps, const char* stage); #endif // --- Kernel Source Code Strings --- // (Alle bisherigen Kernel-Strings bleiben hier unverändert eingefügt) const char *render_kernel_src = "// ----------------------------------------------------------------\n" "// GPU Frame Rendering Kernels\n" "// ----------------------------------------------------------------\n" "typedef struct { float x; float y; } Vec2f;\n" "typedef struct {\n" " float pos_x;\n" " float pos_y;\n" " float hue;\n" " int trail_start;\n" " int trail_len;\n" "} RenderAgent;\n" "\n" "static inline float clamp01(float v` |

| 6321 | `int` | `get_last_quantum_echo_profile` | `QuantumEchoProfile* out_profile` |

| 7264 | `void` | `set_quantum_enabled` | `int enabled` |

| 7275 | `int` | `initialize_gpu` | `int gpu_index` |

| 7859 | `void` | `free_gpu_memory` | `int gpu_index, void* buffer_handle` |

| 7872 | `int` | `write_host_to_gpu_blocking` | `int gpu_index, void* gpu_buffer_handle, size_t offset, size_t size, const void* host_source_ptr` |

| 7907 | `int` | `read_gpu_to_host_blocking` | `int gpu_index, void* gpu_buffer_handle, size_t offset, size_t size, void* host_destination_ptr` |

| 13211 | `void` | `shutdown_gpu` | `int gpu_index` |

| 16035 | `int` | `finish_gpu` | `int gpu_index` |

| 16039 | `const char*` | `cc_get_last_error` | `void` |

| 16043 | `const char*` | `cc_get_version` | `void` |

| 17742 | `int` | `get_last_kernel_metrics` | `int gpu_index, KernelMetricsSample* out_metrics` |

## Tensor / Deep Learning

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 8231 | `int` | `subqg_set_multifield_state` | `int gpu_index, int cell_count, const float* energy, const float* pressure, const float* gravity, const float* magnetism, const float* temperature, const float* potential, const float* drift_x, const float* drift_y` |

| 8266 | `int` | `subqg_get_multifield_state` | `int gpu_index, int max_cells, float* energy, float* pressure, float* gravity, float* magnetism, float* temperature, float* potential, float* drift_x, float* drift_y` |

| 8366 | `int` | `subqg_simulation_step` | `int gpu_index, float rng_energy, float rng_phase, float rng_spin, float* out_energy, float* out_phase, float* out_interference, int* out_node_flag, int* out_spin, int* out_topology, float* out_field_map, int field_map_length` |

| 8556 | `int` | `subqg_simulation_step_batched` | `int gpu_index, const float* rng_energy, const float* rng_phase, const float* rng_spin, int batch_count, float* out_energy, float* out_phase, float* out_interference, int* out_node_flag, int* out_spin, int* out_topology, float* out_field_map, int field_map_length` |

| 16437 | `int` | `execute_matmul_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int B, int M, int N, int K` |

| 16459 | `int` | `execute_softmax_on_gpu` | `int gpu_index, void* buffer_input, void* buffer_output, int num_rows, int row_size` |

| 16466 | `int` | `execute_gelu_on_gpu` | `int gpu_index, void* buffer_input, void* buffer_output, int num_elements` |

| 16473 | `int` | `execute_add_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int num_elements` |

| 16480 | `int` | `execute_add_bias_on_gpu` | `int gpu_index, void* buffer_a_or_c, void* buffer_b_bias, int M, int N` |

| 16487 | `int` | `execute_mul_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int num_elements` |

| 16511 | `int` | `execute_conv2d_forward_on_gpu` | `int gpu_index, void* input, void* weights, void* bias, void* output, int B, int C_in, int H, int W, int C_out, int K_h, int K_w, int stride_h, int stride_w` |

| 16553 | `int` | `execute_conv2d_backward_on_gpu` | `int gpu_index, void* grad_output, void* input, void* weights, void* grad_input, void* grad_weights, void* grad_bias, int B, int C_in, int H, int W, int C_out, int K_h, int K_w, int stride_h, int stride_w` |

| 16661 | `int` | `compute_ctc_loss_cpu` | `const float* logits, int T, int B, int V, const int* targets, int max_target_len, const int* target_lengths, const int* input_lengths, int blank_index, float* loss_out, float* grad_out` |

| 16866 | `int` | `execute_layernorm_on_gpu` | `int gpu_index, void* buffer_input, void* buffer_output, int num_rows, int row_size, float eps` |

| 16874 | `int` | `execute_clone_on_gpu` | `int gpu_index, void* src_buffer, void* dst_buffer, size_t size` |

| 16881 | `int` | `execute_transpose_on_gpu` | `int gpu_index, void* buffer_input, void* buffer_output, int rows, int cols` |

| 16888 | `int` | `execute_gelu_backward_on_gpu` | `int gpu_index, void* buffer_input, void* buffer_grad_output, void* buffer_grad_input, int num_elements` |

| 16895 | `int` | `execute_matmul_backward_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_b, void* buffer_dc, void* buffer_da, void* buffer_db, int B, int M, int N, int K` |

| 16922 | `int` | `execute_layernorm_backward_on_gpu` | `int gpu_index, void* buffer_dy, void* buffer_x, void* buffer_dx, int num_rows, int row_size, float eps` |

| 16930 | `int` | `execute_adam_update_on_gpu` | `int gpu_index, void* param_buffer, void* grad_buffer, void* m_buffer, void* v_buffer, int num_elements, int t, float lr, float beta1, float beta2, float eps, float weight_decay` |

| 16955 | `int` | `execute_softmax_backward_on_gpu` | `int gpu_index, void* buffer_dy, void* buffer_y, void* buffer_dx, int num_rows, int row_size` |

| 16962 | `int` | `execute_mul_backward_on_gpu` | `int gpu_index, void* buffer_dC, void* buffer_A, void* buffer_B, void* buffer_dA, void* buffer_dB, int num_elements` |

| 16970 | `int` | `execute_transpose_backward_on_gpu` | `int gpu_index, void* buffer_dC, void* buffer_dA, int rows_A, int cols_A` |

| 16977 | `int` | `execute_embedding_lookup_gpu` | `int gpu_index, void* idx, void* w, void* o, int b, int s, int d, int v` |

| 16985 | `int` | `execute_embedding_backward_gpu` | `int gpu_index, void* d_o, void* idx, void* d_w, int b, int s, int d, int v` |

| 17024 | `int` | `execute_reduce_sum_gpu` | `int gpu_index, void* in, void* out, int B, int M, int N` |

| 17031 | `int` | `execute_broadcast_add_gpu` | `int gpu_index, void* a, void* b, void* c, int B, int M, int N` |

| 17038 | `int` | `execute_transpose_batched_gpu` | `int gpu_index, void* in, void* out, int B_flat, int d1, int d2` |

| 17045 | `int` | `execute_transpose_12_batched_gpu` | `int gpu_index, void* buffer_in, void* buffer_out, int B, int D1, int D2, int D3` |

| 17052 | `int` | `execute_matmul_batched_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_b, void* buffer_c, int B, int M, int N, int K` |

| 17060 | `int` | `execute_matmul_batched_backward_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_b, void* buffer_dc, void* buffer_da, void* buffer_db, int B, int M, int N, int K` |

| 17087 | `int` | `execute_log_softmax_stable_gpu` | `int gpu_index, void* input_logits, void* output_log_probs, int B_S_rows, int V_cols` |

| 17097 | `int` | `execute_cross_entropy_loss_grad_gpu` | `int gpu_index, void* log_probs, void* target_indices, void* grad_input, void* loss_per_sample, int num_rows, int V` |

| 17104 | `int` | `execute_add_broadcast_pe_gpu` | `int gpu_index, void* input, void* pe_slice, void* output, int B, int S, int E` |

| 17753 | `unsigned long long` | `simulated_kernel_allocate` | `int gpu_index, size_t size` |

| 17759 | `void` | `simulated_kernel_free` | `int gpu_index, unsigned long long address, size_t size` |

| 17763 | `void` | `simulated_kernel_write` | `int gpu_index, unsigned long long address, size_t size, const void *source` |

| 17767 | `unsigned int` | `simulated_get_compute_unit_count` | `int gpu_index` |

## Vision / Patches / Encoder

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 16599 | `int` | `execute_patch_permute_reshape_on_gpu` | `int gpu_index, void* input, void* output, int B, int C, int H, int W` |

| 16617 | `int` | `execute_patch_permute_reshape_backward_on_gpu` | `int gpu_index, void* grad_tokens, void* grad_feature, int B, int C, int H, int W` |

| 16635 | `int` | `execute_eon_encoder_chain_on_gpu` | `int gpu_index, void* buffer_input, void* buffer_output, size_t num_bytes` |

| 16648 | `int` | `execute_eon_encoder_backward_chain_on_gpu` | `int gpu_index, void* buffer_grad_output, void* buffer_grad_input, size_t num_bytes` |

| 17127 | `int` | `execute_dynamic_token_assignment_gpu` | `int gpu_index, void* activations_bse, void* prototypes_te, void* output_indices_bs, int B, int S, int E, int T` |

## Mycelium / Agenten / Kolonie

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 8804 | `int` | `subqg_inject_agents` | `int gpu_index, const HPIOAgent* agents, int count` |

| 8869 | `int` | `update_genetic_agents` | `int gpu_index, const float* agent_states_in, float* agent_states_out, int agent_state_stride, int agent_count, float time_step` |

| 9059 | `int` | `subqg_init_mycel` | `int gpu_index, int T_cap, int C, int K` |

| 9280 | `int` | `subqg_realloc_pheromone_channels` | `int gpu_index, int new_C` |

| 9358 | `int` | `subqg_set_nutrient_recovery` | `int gpu_index, float recovery_rate` |

| 9372 | `int` | `set_pheromone_gains` | `int gpu_index, const float* gain_C, int count` |

| 9443 | `int` | `set_mood_state` | `int gpu_index, const float* mood_tC` |

| 9465 | `int` | `set_nutrient_state` | `int gpu_index, const float* nutrient_t` |

| 9486 | `int` | `step_pheromone_reinforce` | `int gpu_index, const float* activity_t` |

| 9565 | `int` | `step_pheromone_diffuse_decay` | `int gpu_index` |

| 9613 | `int` | `step_mycel_update` | `int gpu_index, const float* activity_t` |

| 9743 | `int` | `step_colony_update` | `int gpu_index, int iterations` |

| 9748 | `int` | `step_reproduction` | `int gpu_index, const float* activity_t, const float* prototypes, int E` |

| 9871 | `int` | `read_pheromone_slice` | `int gpu_index, int channel, float* out_TK` |

| 9957 | `int` | `read_full_pheromone_buffer` | `void* out_buffer, int out_bytes` |

| 10374 | `int` | `read_nutrient` | `int gpu_index, float* out_T` |

| 10477 | `int` | `save_mycel_state` | `int gpu_index, const char* path` |

| 10530 | `int` | `load_mycel_state` | `int gpu_index, const char* path` |

| 10618 | `int` | `step_hebbian_social_learning` | `int gpu_index, float learning_rate` |

| 10673 | `int` | `mycel_agent_cycle` | `int gpu_index, int cycles, float sensory_gain, float learning_rate, float time_step` |

| 10896 | `int` | `cycle_vram_organism` | `int gpu_index, int cycles, float sensory_gain, float learning_rate` |

| 12494 | `int` | `execute_qec_cycle_gpu` | `int gpu_index, int code_type, uint32_t error_mask, float* out_syndrome, int syndrome_length` |

| 17111 | `int` | `execute_hebbian_update_on_gpu` | `int gpu_index, void* buffer_a, void* buffer_c, void* buffer_w, float learning_rate, int B, int M, int N, int K` |

## SubQG / Multi-Field Simulation

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 1997 | `int` | `subqg_initialize_state_batched` | `int gpu_index, int cell_count, const float* initial_energy, const float* initial_phase, float noise_level, float threshold); DLLEXPORT int subqg_set_multifield_state(int gpu_index, int cell_count, const float* energy, const float* pressure, const float* gravity, const float* magnetism, const float* temperature, const float* potential, const float* drift_x, const float* drift_y); DLLEXPORT int subqg_get_multifield_state(int gpu_index, int max_cells, float* energy, float* pressure, float* gravity, float* magnetism, float* temperature, float* potential, float* drift_x, float* drift_y); DLLEXPORT int subqg_debug_read_channel(int gpu_index, int channel, float* out_host, int max_len); DLLEXPORT int subqg_debug_read_field(float* out_host, int max_len); static int ensure_subqg_state(int width, int height` |

| 7969 | `int` | `subqg_initialize_state` | `int gpu_index, float initial_energy, float initial_phase, float noise_level, float threshold` |

| 7973 | `int` | `subqg_initialize_state_batched` | `int gpu_index, int cell_count, const float* initial_energy, const float* initial_phase, float noise_level, float threshold` |

| 8211 | `int` | `subqg_debug_read_field` | `float* out_host, int max_len` |

| 8312 | `int` | `subqg_debug_read_channel` | `int gpu_index, int channel, float* out_host, int max_len` |

| 9268 | `int` | `subqg_set_active_T` | `int gpu_index, int T_act` |

| 9345 | `int` | `subqg_set_repro_params` | `int gpu_index, float thr_nu, float thr_act, float mut_sigma` |

| 9808 | `int` | `step_subqg_feedback` | `int gpu_index, float kappa_nutrient, const float* kappa_mood, int count` |

| 9839 | `int` | `step_potential_for_hpio` | `int gpu_index, const float* mood_weights, int count` |

| 9948 | `int` | `subqg_get_dims` | `int* out_w, int* out_h` |

| 10388 | `int` | `read_potential` | `int gpu_index, float* out_T` |

| 10596 | `void` | `subqg_set_deterministic_mode` | `int enabled, uint64_t seed` |

| 10608 | `void` | `subqg_release_state` | `int gpu_index` |

| 10901 | `void` | `subqg_set_params` | `float noise_level, float threshold` |

| 16189 | `int` | `execute_resonant_field_step_gpu` | `int gpu_index, void* state_buf, void* velocity_buf, void* drive_buf, void* energy_buf, void* neighbors_buf, void* weights_buf, int N, int K, float dt, float damping, float coupling, float inertia, float clamp_abs` |

## Quanten / SQSE / Quantum-Inspired

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 10950 | `int` | `execute_shor_gpu` | `int gpu_index, int modulus_N, int base_a, int* out_period_estimate, float* out_control_distribution, int distribution_length` |

| 11057 | `int` | `execute_grover_gpu` | `int gpu_index, int num_qubits, int iterations, uint64_t marked_mask, uint64_t marked_value, int* out_marked_state, float* out_distribution, int distribution_length` |

| 11128 | `int` | `execute_vqe_gpu` | `int gpu_index, int num_qubits, int ansatz_layers, const float* parameters, int num_parameters, const PauliZTerm* hamiltonian_terms, int num_terms, float* out_energy, float* out_gradients` |

| 11198 | `int` | `execute_vqe_gradients_parallel_gpu` | `int gpu_index, int num_qubits, int ansatz_layers, const float* parameters, int num_parameters, const PauliZTerm* hamiltonian_terms, int num_terms, float* out_energy, float* out_gradients` |

| 12324 | `int` | `execute_qaoa_gpu` | `int gpu_index, int num_qubits, int p_layers, const float* gammas, const float* betas, int num_parameters, const PauliZTerm* cost_terms, int num_cost_terms, float* out_energy` |

| 12370 | `int` | `execute_hhl_gpu` | `int gpu_index, const float* matrix_A, const float* vector_b, int system_size, float* out_solution, int solution_length` |

| 12457 | `int` | `execute_qml_classifier_gpu` | `int gpu_index, int num_qubits, const float* feature_vector, int num_features, const float* parameters, int num_parameters, float* out_expectations, int expectation_length` |

| 12617 | `int` | `quantum_upload_gate_sequence` | `int gpu_index, const QuantumGate* gates, int gate_count` |

| 12664 | `int` | `quantum_apply_gate_sequence` | `int gpu_index, int num_qubits, float* out_probabilities, int probability_length` |

| 12773 | `int` | `quantum_export_to_qasm` | `int gpu_index, const char* filepath` |

| 12824 | `int` | `quantum_import_from_qasm` | `const char* filepath, QuantumGate* out_gates, int max_gates, int* out_gate_count, int* out_num_qubits` |

| 13017 | `int` | `execute_quantum_echoes_otoc_gpu` | `int gpu_index, int num_qubits, const QuantumGate* U_gates, int U_gate_count, const QuantumGate* W_gate, const QuantumGate* V_gate, int measure_otoc2, float* out_L, float* out_otoc2_real, float* out_otoc2_imag` |

| 17488 | `int` | `sqse_load_kernels` | `const char* kernel_path` |

| 17505 | `int` | `execute_sqse_encrypt_float` | `const float* data_in, const float* key, int n, float chaos_K, int steps, float* out_theta, float* out_p_masked` |

| 17618 | `int` | `execute_sqse_decrypt_float` | `const float* in_theta, const float* in_p_masked, const float* key, int n, float chaos_K, int steps, float* data_out` |

## Cognitive / Narrative / Qualia

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 11367 | `int` | `compute_qualia_resonance_gpu` | `int gpu_index, int signal_count, const float* gradient_signal, const float* field_flux_signal, const float* coherence_signal, const float* novelty_signal, float mood_bias, float harmony_gain, float* resonance_vector_out, float* resonance_field_out` |

| 11529 | `int` | `compute_intuition_precognition_gpu` | `int gpu_index, int signal_count, const float* pheromone_signal, const float* field_signal, const float* quantum_signal, float sensitivity, float anticipation_gain, float* intuition_vector_out, float* foresight_field_out` |

| 11674 | `int` | `compute_context_resonance_gpu` | `int gpu_index, int signal_count, const float* stimulus_signal, const float* response_signal, const float* valence_signal, float recency_bias, float significance_scale, float* context_vector_out, float* context_field_out` |

| 11819 | `int` | `generate_dream_state_gpu` | `int gpu_index, int signal_count, const float* qualia_vector, const float* intuition_vector, const float* context_vector, const float* gradient_signal, const float* flux_signal, const float* field_signal, const float* behavior_signal, const float* target_qualia_vector, float* ideal_gradient_out, float* ideal_flux_out, float* ideal_field_out, float* ideal_behavior_out, float* latent_vector_out` |

| 11973 | `int` | `plan_transformation_gpu` | `int gpu_index, int signal_count, const float* current_gradient, const float* current_flux, const float* dream_gradient, const float* dream_flux, const float* qualia_vector, const float* context_vector, float learning_rate, float exploration_bias, float* plan_scores_out, float* plan_matrix_out` |

| 12081 | `int` | `generate_system_narrative_gpu` | `int gpu_index, int signal_count, const float* qualia_vector, const float* intuition_vector, const float* context_vector, const float* dream_latent, int latent_stride, const float* plan_matrix, int plan_stride, const float* plan_scores, float* narrative_embeddings_out, float* narrative_weights_out, float* narrative_summary_out` |

| 12222 | `int` | `abstract_to_symbolic_concepts_gpu` | `int gpu_index, int signal_count, const float* narrative_embeddings, const float* narrative_weights, int embedding_stride, float* concept_codes_out, float* concept_strength_out, float* concept_summary_out` |

## Enterprise Algorithm Pack

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 16266 | `int` | `execute_energy_gated_scheduler_gpu` | `int gpu_index, void* energy_buf, void* nutrient_buf, void* active_flags_buf, void* active_indices_buf, void* active_count_buf, int N, float threshold, float sleep_decay, float nutrient_recovery` |

| 16358 | `int` | `execute_morphogenetic_rule_step_gpu` | `int gpu_index, void* cell_type_buf, void* nutrient_buf, void* energy_buf, void* potential_buf, void* rule_in_type_buf, void* rule_min_nutrient_buf, void* rule_min_energy_buf, void* rule_out_type_buf, void* rule_delta_potential_buf, int N, int R, float nutrient_cost` |

## Noise / Metrics / Kernel-Simulation

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 17722 | `void` | `set_noise_level` | `int gpu_index, float value` |

| 17727 | `float` | `get_noise_level` | `int gpu_index` |

| 17732 | `void` | `register_kernel_measurement_buffers` | `float* error_ptr, float* variance_ptr` |

| 17737 | `void` | `reset_kernel_measurement_buffers` | `void` |

## Sonstige

| Zeile | Rückgabe | Funktion | Parameter |
|---:|---|---|---|

| 9397 | `int` | `set_diffusion_params` | `int gpu_index, float decay_default, float diffu_default` |

| 9421 | `int` | `set_neighbors_sparse` | `int gpu_index, const int* neigh_idx_TK` |

| 9977 | `int` | `render_frame_to_buffer` | `int gpu_index, int width, int height, void* out_buffer_host, const RenderAgent* agents, int num_agents, const Vec2f* trail_points, int num_trail_points, float exposure_scale, float agent_radius, float trail_thickness, float clip_percentile` |

| 10433 | `int` | `read_colonies` | `int gpu_index, uint8_t* out_T` |

| 10918 | `int` | `launch_shadow_self_reenqueue` | `int gpu_index, int work_items, int max_generations` |

| 17120 | `int` | `execute_threshold_spike_on_gpu` | `int gpu_index, void* buffer_activations, void* buffer_spikes, float threshold, int num_elements` |

| 17135 | `int` | `execute_pairwise_similarity_gpu` | `int gpu_index, void* states_nd, void* output_similarity_nn, int N, int D` |

| 17143 | `int` | `execute_proto_segmented_sum_gpu` | `int gpu_index, void* activations_flat, void* indices_flat, void* proto_sums, void* proto_counts, int num_elements_flat, int E, int T` |

| 17152 | `int` | `execute_proto_update_step_gpu` | `int gpu_index, void* prototypes, void* proto_sums, void* proto_counts, float learning_rate, int E, int T` |

| 17161 | `int` | `execute_shape_loss_with_reward_penalty_gpu` | `int gpu_index, void* loss_per_sample_in, void* predictions, void* targets, void* loss_per_sample_out, int num_samples, int num_classes, float penalty_weight, float reward_weight, float high_confidence_threshold, int critical_target_class, int critical_predicted_class` |

| 17195 | `int` | `execute_fused_diffusion_on_gpu` | `int gpu_index, void* buffer_X, void* buffer_W, void* buffer_O, int B, int N, int D, float gamma, float sigma` |

| 17259 | `int` | `execute_izhikevich_step_on_gpu` | `int gpu_index, void* v, void* u, void* i_inj, void* spikes_out, void* p_a, void* p_b, void* p_c, void* p_d, float dt, float threshold, int num_neurons` |

| 17291 | `int` | `execute_stdp_update_on_gpu` | `int gpu_index, void* weights, void* pre_traces, void* post_traces, void* pre_spike_events, void* post_spike_events, float lr_ltp, float lr_ltd, int pre_n, int post_n` |

| 17320 | `int` | `execute_stdp_trace_update_on_gpu` | `int gpu_index, void* pre_traces, void* post_traces, void* pre_spike_events, void* post_spike_events, float decay_pre, float decay_post, float increment_pre, float increment_post, int pre_n, int post_n` |

| 17348 | `int` | `execute_lbm_collide_and_stream_on_gpu` | `int gpu_index, void* f_in, void* f_out, void* rho, void* ux, void* uy, float omega, int width, int height` |

| 17377 | `int` | `execute_nbody_calculate_forces_on_gpu` | `int gpu_index, void* positions, void* forces, float gravitational_const, float softening_factor, int num_bodies` |

| 17399 | `int` | `execute_nbody_integrate_on_gpu` | `int gpu_index, void* positions, void* velocities, void* forces, float dt, int num_bodies` |

| 17421 | `int` | `execute_ising_metropolis_step_on_gpu` | `int gpu_index, void* spin_grid, void* random_numbers, float J, float beta, int width, int height, int color` |

| 17449 | `int` | `execute_shape_loss_with_reward_penalty_list_gpu` | `int gpu_index, void* loss_per_sample_in, void* predictions, void* targets, void* loss_per_sample_out, void* critical_pairs, int num_samples, int num_classes, int num_critical_pairs, float penalty_weight, float reward_weight, float high_confidence_threshold` |
