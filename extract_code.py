import os

def extract_android_code(root_dir, output_file):
    # 我们需要提取的核心文件类型
    valid_extensions = {'.kt', '.kts', '.gradle', '.xml'}
    
    # 必须跳过的构建缓存和隐藏目录
    ignore_dirs = {'build', '.git', '.idea', '.gradle', 'gradle'}

    # 统计一下提取了多少文件
    file_count = 0

    with open(output_file, 'w', encoding='utf-8') as outfile:
        for dirpath, dirnames, filenames in os.walk(root_dir):
            # 原地修改 dirnames，遇到忽略目录直接跳过，加快遍历速度
            dirnames[:] = [d for d in dirnames if d not in ignore_dirs]

            for filename in filenames:
                ext = os.path.splitext(filename)[1].lower()
                
                # 如果是 xml，我们最好只提取核心的 Manifest 或特定的资源，避免提取太多冗余的矢量图
                # 这里为了稳妥，全提取也行，但可以视情况过滤
                if ext in valid_extensions:
                    filepath = os.path.join(dirpath, filename)
                    try:
                        with open(filepath, 'r', encoding='utf-8') as infile:
                            content = infile.read()
                            
                            # 添加明显的分隔符和相对路径，方便大语言模型阅读和定位上下文
                            rel_path = os.path.relpath(filepath, root_dir)
                            outfile.write(f"\n{'='*60}\n")
                            outfile.write(f"File: {rel_path}\n")
                            outfile.write(f"{'='*60}\n\n")
                            outfile.write(content)
                            outfile.write("\n")
                            file_count += 1
                    except Exception as e:
                        print(f"跳过无法读取的文件 {filepath}: {e}")

    print(f"✨ 提取完成！共合并了 {file_count} 个文件。")
    print(f"📄 代码已导出至: {output_file}")

if __name__ == "__main__":
    # 替换为你安卓项目的根目录路径，如果在项目根目录运行，直接用 "."
    project_root = "." 
    output_filename = "android_project_context.txt"
    
    print("开始扫描和整合代码...")
    extract_android_code(project_root, output_filename)