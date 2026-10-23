{ config, pkgs, ... }:

{
    services.ollama = {
        enable = true;
        package = pkgs.ollama-cuda;

        # Models to `ollama pull` on boot (see https://ollama.com/library).
        loadModels = [
            "llama3.1"
        ];
    };
}
