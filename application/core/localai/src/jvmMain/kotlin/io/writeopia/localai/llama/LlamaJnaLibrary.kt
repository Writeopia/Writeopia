package io.writeopia.localai.llama

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * JNA interface for llama.cpp library.
 * Provides bindings to the native llama.cpp C API.
 *
 * The library is loaded from bundled resources first, then falls back to system paths.
 * Bundled libraries should be placed in: resources/natives/{os}-{arch}/libllama.{ext}
 *
 * Supported platforms:
 * - macos-arm64/libllama.dylib
 * - macos-x64/libllama.dylib
 * - linux-x64/libllama.so
 * - linux-arm64/libllama.so
 * - windows-x64/llama.dll
 */
@Suppress("FunctionName")
interface LlamaJnaLibrary : Library {
    companion object {
        private const val LIBRARY_NAME = "llama"

        val INSTANCE: LlamaJnaLibrary? by lazy {
            // First, ensure the native library is loaded/extracted
            if (!NativeLibraryLoader.loadLibrary()) {
                println("[LlamaJNA] Failed to load native library: ${NativeLibraryLoader.getLoadError()}")
                return@lazy null
            }

            try {
                // Now load via JNA - it will find the already-loaded library
                Native.load(LIBRARY_NAME, LlamaJnaLibrary::class.java)
            } catch (e: UnsatisfiedLinkError) {
                println("[LlamaJNA] JNA binding failed: ${e.message}")
                null
            }
        }

        fun isAvailable(): Boolean = INSTANCE != null

        fun getLoadError(): String? = NativeLibraryLoader.getLoadError()
    }

    // Backend initialization
    fun llama_backend_init()
    fun llama_backend_free()

    // Model loading
    fun llama_load_model_from_file(path_model: String, params: LlamaModelParamsNative.ByValue): Pointer?
    fun llama_free_model(model: Pointer)
    fun llama_model_default_params(): LlamaModelParamsNative.ByValue

    // Context creation
    fun llama_new_context_with_model(model: Pointer, params: LlamaContextParamsNative.ByValue): Pointer?
    fun llama_free(ctx: Pointer)
    fun llama_context_default_params(): LlamaContextParamsNative.ByValue

    // Tokenization
    fun llama_tokenize(
        model: Pointer,
        text: String,
        text_len: Int,
        tokens: IntArray,
        n_tokens_max: Int,
        add_special: Boolean,
        parse_special: Boolean
    ): Int

    fun llama_token_to_piece(
        model: Pointer,
        token: Int,
        buf: ByteArray,
        length: Int,
        lstrip: Int,
        special: Boolean
    ): Int

    // Generation
    fun llama_decode(ctx: Pointer, batch: LlamaBatchNative.ByValue): Int
    fun llama_get_logits(ctx: Pointer): Pointer?
    fun llama_n_vocab(model: Pointer): Int
    fun llama_n_ctx(ctx: Pointer): Int

    // Sampling
    fun llama_sampler_chain_init(params: LlamaSamplerChainParamsNative.ByValue): Pointer?
    fun llama_sampler_chain_add(chain: Pointer, sampler: Pointer)
    fun llama_sampler_chain_free(chain: Pointer)
    fun llama_sampler_init_temp(temp: Float): Pointer?
    fun llama_sampler_init_dist(seed: Int): Pointer?
    fun llama_sampler_sample(sampler: Pointer, ctx: Pointer, idx: Int): Int

    // Batch operations
    fun llama_batch_init(n_tokens: Int, embd: Int, n_seq_max: Int): LlamaBatchNative.ByValue
    fun llama_batch_free(batch: LlamaBatchNative.ByValue)

    // Misc
    fun llama_token_eos(model: Pointer): Int
    fun llama_token_bos(model: Pointer): Int
    fun llama_kv_cache_clear(ctx: Pointer)
}

/**
 * Native structure for llama_model_params.
 */
@Structure.FieldOrder(
    "n_gpu_layers", "split_mode", "main_gpu", "tensor_split",
    "rpc_servers", "progress_callback", "progress_callback_user_data",
    "kv_overrides", "vocab_only", "use_mmap", "use_mlock", "check_tensors"
)
open class LlamaModelParamsNative(
    @JvmField var n_gpu_layers: Int = 0,
    @JvmField var split_mode: Int = 0,
    @JvmField var main_gpu: Int = 0,
    @JvmField var tensor_split: Pointer? = null,
    @JvmField var rpc_servers: Pointer? = null,
    @JvmField var progress_callback: Pointer? = null,
    @JvmField var progress_callback_user_data: Pointer? = null,
    @JvmField var kv_overrides: Pointer? = null,
    @JvmField var vocab_only: Boolean = false,
    @JvmField var use_mmap: Boolean = true,
    @JvmField var use_mlock: Boolean = false,
    @JvmField var check_tensors: Boolean = false
) : Structure() {
    class ByValue : LlamaModelParamsNative(), Structure.ByValue
    class ByReference : LlamaModelParamsNative(), Structure.ByReference
}

/**
 * Native structure for llama_context_params.
 */
@Structure.FieldOrder(
    "n_ctx", "n_batch", "n_ubatch", "n_seq_max", "n_threads", "n_threads_batch",
    "rope_scaling_type", "pooling_type", "attention_type",
    "rope_freq_base", "rope_freq_scale", "yarn_ext_factor", "yarn_attn_factor",
    "yarn_beta_fast", "yarn_beta_slow", "yarn_orig_ctx", "defrag_thold",
    "cb_eval", "cb_eval_user_data", "type_k", "type_v",
    "logits_all", "embeddings", "offload_kqv", "flash_attn", "no_perf", "abort_callback",
    "abort_callback_data"
)
open class LlamaContextParamsNative(
    @JvmField var n_ctx: Int = 2048,
    @JvmField var n_batch: Int = 512,
    @JvmField var n_ubatch: Int = 512,
    @JvmField var n_seq_max: Int = 1,
    @JvmField var n_threads: Int = 4,
    @JvmField var n_threads_batch: Int = 4,
    @JvmField var rope_scaling_type: Int = -1,
    @JvmField var pooling_type: Int = -1,
    @JvmField var attention_type: Int = 0,
    @JvmField var rope_freq_base: Float = 0f,
    @JvmField var rope_freq_scale: Float = 0f,
    @JvmField var yarn_ext_factor: Float = -1f,
    @JvmField var yarn_attn_factor: Float = 1f,
    @JvmField var yarn_beta_fast: Float = 32f,
    @JvmField var yarn_beta_slow: Float = 1f,
    @JvmField var yarn_orig_ctx: Int = 0,
    @JvmField var defrag_thold: Float = -1f,
    @JvmField var cb_eval: Pointer? = null,
    @JvmField var cb_eval_user_data: Pointer? = null,
    @JvmField var type_k: Int = 1,
    @JvmField var type_v: Int = 1,
    @JvmField var logits_all: Boolean = false,
    @JvmField var embeddings: Boolean = false,
    @JvmField var offload_kqv: Boolean = true,
    @JvmField var flash_attn: Boolean = false,
    @JvmField var no_perf: Boolean = true,
    @JvmField var abort_callback: Pointer? = null,
    @JvmField var abort_callback_data: Pointer? = null
) : Structure() {
    class ByValue : LlamaContextParamsNative(), Structure.ByValue
    class ByReference : LlamaContextParamsNative(), Structure.ByReference
}

/**
 * Native structure for llama_batch.
 */
@Structure.FieldOrder(
    "n_tokens", "token", "embd", "pos", "n_seq_id", "seq_id", "logits"
)
open class LlamaBatchNative(
    @JvmField var n_tokens: Int = 0,
    @JvmField var token: Pointer? = null,
    @JvmField var embd: Pointer? = null,
    @JvmField var pos: Pointer? = null,
    @JvmField var n_seq_id: Pointer? = null,
    @JvmField var seq_id: Pointer? = null,
    @JvmField var logits: Pointer? = null
) : Structure() {
    class ByValue : LlamaBatchNative(), Structure.ByValue
    class ByReference : LlamaBatchNative(), Structure.ByReference
}

/**
 * Native structure for llama_sampler_chain_params.
 */
@Structure.FieldOrder("no_perf")
open class LlamaSamplerChainParamsNative(
    @JvmField var no_perf: Boolean = true
) : Structure() {
    class ByValue : LlamaSamplerChainParamsNative(), Structure.ByValue
    class ByReference : LlamaSamplerChainParamsNative(), Structure.ByReference
}
