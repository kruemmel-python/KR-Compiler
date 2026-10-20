#pragma once

#include <SDL3/SDL.h>
#include <array>
#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace mdraft {

class GapBuffer {
public:
    explicit GapBuffer(std::size_t capacity = 4096);
    explicit GapBuffer(const std::string& text);

    std::size_t size() const noexcept;
    std::size_t cursor() const noexcept;
    bool empty() const noexcept;
    void set_text(const std::string& text);
    std::string str() const;
    void move_to(std::size_t byte_index);
    void move_left();
    void move_right();
    void move_home();
    void move_end();
    void move_vertical(int rows);
    void move_to_line_column(std::size_t line, std::size_t column);
    std::pair<std::size_t, std::size_t> line_column() const;
    void insert_text(const std::string& text);
    bool erase_before();
    bool erase_at();

private:
    std::vector<char> storage_;
    std::size_t gap_begin_{0};
    std::size_t gap_end_{0};
    void ensure_gap(std::size_t need);
    void move_gap(std::size_t position);
};

enum class BlockKind : std::uint8_t {
    Paragraph,
    Heading,
    Quote,
    ListItem,
    TaskItem,
    CodeFence,
    TableRow,
    Blank
};

struct Block {
    BlockKind kind{BlockKind::Paragraph};
    std::size_t begin{0};
    std::size_t end{0};
    std::uint8_t level{0};
    bool checked{false};
};

std::vector<Block> parse_blocks(const std::string& text);
std::string preview_text(const std::string& text, const Block& block);

struct EditorState {
    std::string path{"mdraft_document.md"};
    std::string status{"Bereit"};
    bool preview{false};
    bool dirty{false};
    bool running{true};
    int scroll_row{0};
};

bool load_document(GapBuffer& buffer, EditorState& state, const std::string& path);
bool save_document(const GapBuffer& buffer, EditorState& state);

struct UiLayout {
    SDL_FRect new_button{};
    SDL_FRect save_button{};
    SDL_FRect mode_button{};
    SDL_FRect content{};
};

int bitmap_text_width(std::string_view text, int scale);
void draw_bitmap_text(SDL_Renderer* renderer, std::string_view text,
                      int x, int y, int scale, SDL_Color color);
void compose_theme_channels(float* output, const float* base,
                            const float* accent, int count);
UiLayout render_editor(SDL_Renderer* renderer, SDL_Window* window,
                       const GapBuffer& buffer, const EditorState& state,
                       std::uint64_t ticks);
int run_application(int argc, char** argv);

} // namespace mdraft
