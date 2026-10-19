// C interface for SymBio GPU routines. Exposes the data structures and entry
// points required to exchange agent and field state between host code and the
// OpenCL kernels used throughout the project.
#ifndef SYMBIO_INTERFACE_H
#define SYMBIO_INTERFACE_H

#ifdef __cplusplus
extern "C" {
#endif


#if defined(_WIN32) || defined(_WIN64)
  #define SYMBIO_API __declspec(dllexport)
#else
  #define SYMBIO_API __attribute__((visibility("default")))
#endif

// High-level agent representation shared between host-side control logic and
// GPU kernels.
typedef struct {
    float x;
    float y;
    float energy;
    float coupling;
} HPIOAgent;

// Views over multi-channel scalar fields stored on the host so they can be
// transferred to or from GPU buffers in a consistent order.
typedef struct {
    float* energy;
    float* pressure;
    float* gravity;
    float* magnetism;
    float* temperature;
    float* potential;
    float* drift_x;
    float* drift_y;
    int    cell_count;
} SubQGMultiFieldHostView;

// Upload multifield state from the host into GPU buffers for simulation.
SYMBIO_API int subqg_set_multifield_state(int gpu_index,
                               int cell_count,
                               const float* energy,
                               const float* pressure,
                               const float* gravity,
                               const float* magnetism,
                               const float* temperature,
                               const float* potential,
                               const float* drift_x,
                               const float* drift_y);

// Retrieve multifield state from the GPU into host-accessible buffers for
// inspection or checkpointing.
SYMBIO_API int subqg_get_multifield_state(int gpu_index,
                               int max_cells,
                               float* energy,
                               float* pressure,
                               float* gravity,
                               float* magnetism,
                               float* temperature,
                               float* potential,
                               float* drift_x,
                               float* drift_y);

// Read back a specific debug channel from the GPU to help diagnose kernels.
SYMBIO_API int subqg_debug_read_channel(int gpu_index,
                             int channel,
                             float* out_host,
                             int max_len);

// Advance genetic agents on the GPU and emit the updated state buffer.
SYMBIO_API int update_genetic_agents(int gpu_index,
                          const float* agent_states_in,
                          float* agent_states_out,
                          int state_stride,
                          int agent_count,
                          float time_step);

// ---------------- Neuropersona GPU API ----------------
// Initialize GPU-side buffers for the Neuropersona state.
// weight_mode: 0 = dense, 1 = csr (reserved for future).
int neuropersona_gpu_init(int gpu_index, int node_count, int weight_mode, int seed);

// Execute one online training step on the GPU.
// input_signal: length node_count or NULL to reuse previous signal.
// input_category_index: -1 to skip explicit category cue.
// out_metric: optional pointer for loss/metric output (may be NULL).
int neuropersona_gpu_train_step(int gpu_index,
                                int node_count,
                                const float* input_signal,
                                int input_category_index,
                                float learning_rate,
                                float decay_rate,
                                float reward_factor,
                                float noise_level,
                                float* out_metric);

// Recall top-k categories for a cue vector.
int neuropersona_gpu_recall(int gpu_index,
                            int node_count,
                            const float* cue_vector,
                            int top_k,
                            int* out_indices,
                            float* out_scores);

// Persist GPU-side checkpoint to a file.
int neuropersona_gpu_checkpoint_save(int gpu_index, const char* path);

// Load GPU-side checkpoint from a file.
int neuropersona_gpu_checkpoint_load(int gpu_index, const char* path);


// ---------------- Enterprise Algorithm Pack API ----------------
// Resonant field propagation over a fixed-K neighbor graph.
// Buffers are OpenCL cl_mem handles passed as void* for ABI stability.
SYMBIO_API int execute_resonant_field_step_gpu(
    int gpu_index,
    void* state_buf,
    void* velocity_buf,
    void* drive_buf,
    void* energy_buf,
    void* neighbors_buf,
    void* weights_buf,
    int N,
    int K,
    float dt,
    float damping,
    float coupling,
    float inertia,
    float clamp_abs);

// GPU-side activity compaction plus cheap sleep-state decay.
// active_count_buf must be a cl_mem buffer with room for one uint.
SYMBIO_API int execute_energy_gated_scheduler_gpu(
    int gpu_index,
    void* energy_buf,
    void* nutrient_buf,
    void* active_flags_buf,
    void* active_indices_buf,
    void* active_count_buf,
    int N,
    float threshold,
    float sleep_decay,
    float nutrient_recovery);

// Tabular morphogenesis rule execution.
// cell_type_buf is uchar[N]; rule_in_type/rule_out_type are int[R].
SYMBIO_API int execute_morphogenetic_rule_step_gpu(
    int gpu_index,
    void* cell_type_buf,
    void* nutrient_buf,
    void* energy_buf,
    void* potential_buf,
    void* rule_in_type_buf,
    void* rule_min_nutrient_buf,
    void* rule_min_energy_buf,
    void* rule_out_type_buf,
    void* rule_delta_potential_buf,
    int N,
    int R,
    float nutrient_cost);


#ifdef __cplusplus
}
#endif

#endif /* SYMBIO_INTERFACE_H */
