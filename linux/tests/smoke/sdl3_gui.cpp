#include <SDL3/SDL.h>
#include <iostream>

int main() {
    if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) return 1;
    SDL_Window* window = SDL_CreateWindow("KR SDL3 smoke", 320, 200, SDL_WINDOW_HIDDEN);
    if (!window) {
        SDL_Quit();
        return 2;
    }
    SDL_DestroyWindow(window);
    SDL_Quit();
    std::cout << "KR-SDL3-GUI-OK\n";
    return 0;
}
