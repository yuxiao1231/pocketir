import os
import sqlite3

def build_database(ir_repo_path, output_db_path):
    if os.path.exists(output_db_path):
        os.remove(output_db_path)
    
    conn = sqlite3.connect(output_db_path)
    cursor = conn.cursor()
    
    # 🌟 手术核心 1：扩展表结构，加入 type, frequency, raw_data
    cursor.execute("""
        CREATE TABLE ir_commands (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category TEXT,
            brand TEXT,
            model TEXT,
            btn_name TEXT,
            type TEXT,
            protocol TEXT,
            address TEXT,
            command TEXT,
            frequency INTEGER,
            raw_data TEXT
        )
    """)
    cursor.execute("CREATE INDEX idx_device ON ir_commands(category, brand, model);")
    
    insert_query = """
        INSERT INTO ir_commands (category, brand, model, btn_name, type, protocol, address, command, frequency, raw_data)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    count = 0
    print("开始执行全能型固实化 (支持 PARSED & RAW)...")
    
    for root, dirs, files in os.walk(ir_repo_path):
        for file in files:
            if file.endswith('.ir'):
                file_path = os.path.join(root, file)
                
                rel_path = os.path.relpath(file_path, ir_repo_path)
                parts = rel_path.split(os.sep)
                if len(parts) >= 3:
                    category, brand, model = parts[0], parts[1], os.path.splitext(parts[-1])[0]
                elif len(parts) == 2:
                    category, brand, model = "General", parts[0], os.path.splitext(parts[-1])[0]
                else:
                    continue

                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    cmd_data = {}
                    
                    def save_current_command():
                        nonlocal count
                        if 'name' in cmd_data and 'type' in cmd_data:
                            # 提取所有可能存在的字段，没有的填 None
                            cursor.execute(insert_query, (
                                category, brand, model,
                                cmd_data.get('name'),
                                cmd_data.get('type').lower(),
                                cmd_data.get('protocol'),
                                cmd_data.get('address'),
                                cmd_data.get('command'),
                                int(cmd_data['frequency']) if 'frequency' in cmd_data else None,
                                cmd_data.get('data')
                            ))
                            count += 1

                    for line in f:
                        line = line.strip()
                        if not line or line.startswith('#'): continue
                            
                        if ':' in line:
                            key, val = [x.strip() for x in line.split(':', 1)]
                            if key == 'name':
                                save_current_command() # 遇到新按钮，先保存上一个
                                cmd_data = {'name': val} # 重新初始化
                            else:
                                cmd_data[key] = val
                    
                    save_current_command() # 保存文件最后一个按钮

    conn.commit()
    cursor.execute("VACUUM;")
    conn.close()
    print(f"✨ 固实化完成！共成功桥接了 {count} 个红外指令！")

if __name__ == "__main__":
    # 填入你 Flipper IR 库的真实路径
    flipper_repo_dir = "./" 
    output_db = "pocket_ir.db"
    build_database(flipper_repo_dir, output_db)